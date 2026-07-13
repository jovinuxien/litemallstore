package org.linlinjava.litemall.order.infrastructure.acl.printer.yly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal Yilianyun (易联云 / Yly) open-platform v2 client — cloud thermal-receipt printers,
 * the printer stack crmeb ships. OkHttp DIRECTLY (no RestTemplate bean, no Feign — this is
 * an external vendor with its own auth dance, and the module's Feign config is tuned for
 * internal/CJ calls). All calls are form-encoded POSTs; all failures are logged WARN and
 * surfaced as {@code false}/{@code null} — this client never throws to callers.
 *
 * <p>Auth (docs: yly open platform v2, self-owned-app / client_credentials flow):
 * every request signs with {@code sign = lowercase MD5(client_id + timestamp + client_secret)},
 * {@code timestamp} in epoch SECONDS, {@code id} a per-request UUID. The access token from
 * {@code POST /oauth/oauth} is Caffeine-cached for {@code min(expires_in, 30 days)}
 * (Yly tokens live ~35 days; 30 keeps a refresh margin).
 *
 * <p>{@code POST /printer/addprinter} (bind machine_code+msign to the app) runs LAZILY
 * exactly once per process before the first print ({@link AtomicBoolean}); "printer
 * already added" answers count as success so restarts don't fail printing.
 */
public class YlyOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(YlyOpenApiClient.class);

    /**
     * Yly error codes that mean "this printer is already bound to this app" — success for
     * our purposes. Best-effort set from the v2 docs/crmeb usage; unknown non-zero codes
     * on addprinter are logged but do NOT block printing (the print call is the arbiter).
     */
    private static final Set<String> ALREADY_ADDED_CODES = Set.of("2", "1013", "1014");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient http;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String machineCode;
    private final String machineSecret;

    private final AtomicBoolean printerAdded = new AtomicBoolean(false);

    /** value = access token; per-entry TTL = min(expires_in, 30d), set at write time. */
    private final Cache<String, TimedToken> tokenCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfter(new Expiry<String, TimedToken>() {
                @Override
                public long expireAfterCreate(String key, TimedToken value, long currentTime) {
                    return value.ttlNanos;
                }

                @Override
                public long expireAfterUpdate(String key, TimedToken value, long currentTime,
                                              long currentDuration) {
                    return value.ttlNanos;
                }

                @Override
                public long expireAfterRead(String key, TimedToken value, long currentTime,
                                            long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    public YlyOpenApiClient(String baseUrl, String clientId, String clientSecret,
                            String machineCode, String machineSecret,
                            long connectTimeoutMs, long readTimeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.machineCode = machineCode;
        this.machineSecret = machineSecret;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    /**
     * Print {@code content} on the configured machine. {@code originId} is Yly's
     * exactly-once key ({@code origin_id}) — a repeated originId is deduped vendor-side.
     *
     * @return true when Yly accepted the job ({@code error == "0"}); false on ANY problem.
     */
    public boolean print(String originId, String content) {
        String token = accessToken();
        if (token == null) {
            return false;
        }
        ensurePrinterAdded(token);
        Map<String, String> form = authedForm(token);
        form.put("machine_code", machineCode);
        form.put("origin_id", originId);
        form.put("content", content);
        JsonNode response = post("/print/index", form);
        if (response == null) {
            return false;
        }
        if (!"0".equals(response.path("error").asText())) {
            log.warn("Yly print rejected (originId={}): error={} {}", originId,
                    response.path("error").asText(), response.path("error_description").asText());
            return false;
        }
        return true;
    }

    // ---- auth -------------------------------------------------------------------------

    private String accessToken() {
        TimedToken cached = tokenCache.get("token", key -> fetchToken());
        return cached == null ? null : cached.token;
    }

    private TimedToken fetchToken() {
        long timestamp = System.currentTimeMillis() / 1000;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "client_credentials");
        form.put("sign", sign(timestamp));
        form.put("scope", "all");
        form.put("timestamp", String.valueOf(timestamp));
        form.put("id", UUID.randomUUID().toString());
        JsonNode response = post("/oauth/oauth", form);
        if (response == null || !"0".equals(response.path("error").asText())) {
            log.warn("Yly token request failed: {}", response == null
                    ? "no/invalid response" : response.path("error_description").asText());
            return null; // Caffeine does not cache null → next print retries.
        }
        JsonNode body = response.path("body");
        String token = body.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            log.warn("Yly token response carried no access_token");
            return null;
        }
        long expiresIn = body.path("expires_in").asLong(TimeUnit.DAYS.toSeconds(30));
        long ttlSeconds = Math.min(expiresIn, TimeUnit.DAYS.toSeconds(30));
        return new TimedToken(token, TimeUnit.SECONDS.toNanos(Math.max(60, ttlSeconds)));
    }

    /** Lazy one-shot printer binding; "already added" is success. Never blocks printing. */
    private void ensurePrinterAdded(String token) {
        if (!printerAdded.compareAndSet(false, true)) {
            return;
        }
        Map<String, String> form = authedForm(token);
        form.put("machine_code", machineCode);
        form.put("msign", machineSecret);
        JsonNode response = post("/printer/addprinter", form);
        if (response == null) {
            // Transport problem: allow a later print to retry the binding.
            printerAdded.set(false);
            return;
        }
        String error = response.path("error").asText();
        if ("0".equals(error) || ALREADY_ADDED_CODES.contains(error)
                || response.path("error_description").asText("").toLowerCase().contains("added")) {
            log.info("Yly printer {} bound (error={})", machineCode, error);
        } else {
            // Unknown rejection: log it, keep the flag set (retrying every print would
            // just spam Yly) and let /print/index be the arbiter.
            log.warn("Yly addprinter answered error={} {} — proceeding; print/index decides",
                    error, response.path("error_description").asText());
        }
    }

    private Map<String, String> authedForm(String token) {
        long timestamp = System.currentTimeMillis() / 1000;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("access_token", token);
        form.put("timestamp", String.valueOf(timestamp));
        form.put("sign", sign(timestamp));
        form.put("id", UUID.randomUUID().toString());
        return form;
    }

    /** Yly v2 signature: lowercase hex MD5 of {@code client_id + timestamp + client_secret}. */
    private String sign(long timestamp) {
        return md5Hex(clientId + timestamp + clientSecret);
    }

    // ---- transport ----------------------------------------------------------------------

    /** Form-encoded POST; null on ANY transport/HTTP/parse problem (already logged). */
    private JsonNode post(String path, Map<String, String> form) {
        FormBody.Builder body = new FormBody.Builder();
        form.forEach(body::add);
        Request request = new Request.Builder().url(baseUrl + path).post(body.build()).build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Yly {} answered HTTP {}", path, response.code());
                return null;
            }
            return objectMapper.readTree(response.body().string());
        } catch (Exception e) {
            log.warn("Yly {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    private static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e); // JRE-mandatory algorithm
        }
    }

    private record TimedToken(String token, long ttlNanos) {
    }
}
