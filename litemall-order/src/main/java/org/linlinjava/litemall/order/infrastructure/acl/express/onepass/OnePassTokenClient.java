package org.linlinjava.litemall.order.infrastructure.acl.express.onepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * OnePass (crmeb's sms.crmeb.net service hub) login-token client, Caffeine-cached ~2h.
 * REUSABLE on purpose: the same account + Bearer- token covers OnePass's other verticals
 * (SMS, copy-goods, ...) — a future SmsSender adapter should inject THIS client rather
 * than log in again. OnePass SMS itself is OUT of scope for Wave 4.
 *
 * <p>Auth shape ({@code POST /user/login} form {@code account}, {@code secret}): the
 * configured secret is sent AS-IS. crmeb's PHP/Java clients hash md5(account+md5-ish
 * material) UPSTREAM when the merchant registers, then store and send the resulting
 * digest — so whatever value works for a crmeb install is the value to configure here;
 * this client applies no hashing of its own. Token is read from {@code data.access_token}
 * (also accepts top-level {@code access_token} — OnePass envelopes vary by version).
 * Auth failure → WARN + empty, cached NOT at all (next query retries).
 */
public class OnePassTokenClient {

    private static final Logger log = LoggerFactory.getLogger(OnePassTokenClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient http;
    private final String baseUrl;
    private final String account;
    private final String secret;

    private final Cache<String, String> tokenCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofHours(2))
            .build();

    public OnePassTokenClient(String baseUrl, String account, String secret,
                              long connectTimeoutMs, long readTimeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.account = account;
        this.secret = secret;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    /** Cached login token; empty on auth failure (already WARN-logged, never throws). */
    public Optional<String> token() {
        return Optional.ofNullable(tokenCache.get("token", key -> login()));
    }

    private String login() {
        Request request = new Request.Builder()
                .url(baseUrl + "/user/login")
                .post(new FormBody.Builder()
                        .add("account", account)
                        .add("secret", secret)
                        .build())
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("OnePass login answered HTTP {}", response.code());
                return null;
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            String token = json.path("data").path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                token = json.path("access_token").asText(null);
            }
            if (token == null || token.isBlank()) {
                log.warn("OnePass login rejected: {}", json.path("msg").asText("no access_token in response"));
                return null;
            }
            return token;
        } catch (Exception e) {
            log.warn("OnePass login failed: {}", e.getMessage());
            return null;
        }
    }
}
