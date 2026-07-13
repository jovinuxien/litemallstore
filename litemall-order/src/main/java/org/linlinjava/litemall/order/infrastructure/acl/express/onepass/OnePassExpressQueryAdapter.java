package org.linlinjava.litemall.order.infrastructure.acl.express.onepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link ExpressQueryPort} over OnePass's express-query vertical (provider {@code onepass},
 * {@code POST /v2/expr/query} form {@code com} = carrier code + {@code num} = tracking
 * number). Call shape is FROM THE CRMEB REFERENCE implementation — live verification
 * requires real OnePass credentials (docs/adr-fulfillment-seams.md).
 *
 * <p>Auth header is {@code Authorization: Bearer-<token>} — note the DASH, crmeb's
 * convention, not standard {@code Bearer <token>}. Response parsing is deliberately
 * tolerant: the event list is looked for under {@code data.list[]} then {@code data[]},
 * and each entry's timestamp/text under {@code time}/{@code status} with common aliases.
 * Non-200 / parse problems / auth failures → WARN + empty, never a throw.
 */
public class OnePassExpressQueryAdapter implements ExpressQueryPort {

    private static final Logger log = LoggerFactory.getLogger(OnePassExpressQueryAdapter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OnePassTokenClient tokenClient;
    private final OkHttpClient http;
    private final String baseUrl;

    public OnePassExpressQueryAdapter(OnePassTokenClient tokenClient, String baseUrl,
                                      long connectTimeoutMs, long readTimeoutMs) {
        this.tokenClient = tokenClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Override
    public Optional<ExpressTrackingSnapshot> query(String carrier, String trackNumber) {
        if (trackNumber == null || trackNumber.isBlank()) {
            return Optional.empty();
        }
        Optional<String> token = tokenClient.token();
        if (token.isEmpty()) {
            return Optional.empty(); // login already WARN-logged
        }
        Request request = new Request.Builder()
                .url(baseUrl + "/v2/expr/query")
                .header("Authorization", "Bearer-" + token.get())
                .post(new FormBody.Builder()
                        .add("com", carrier == null ? "" : carrier.trim())
                        .add("num", trackNumber.trim())
                        .build())
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("OnePass expr/query answered HTTP {} for {} / {}",
                        response.code(), carrier, trackNumber);
                return Optional.empty();
            }
            return parse(objectMapper.readTree(response.body().string()), carrier, trackNumber);
        } catch (Exception e) {
            log.warn("OnePass expr/query failed for {} / {}: {}", carrier, trackNumber, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    private Optional<ExpressTrackingSnapshot> parse(JsonNode json, String carrier, String trackNumber) {
        int status = json.path("status").asInt(json.path("code").asInt(200));
        if (status != 200) {
            log.warn("OnePass expr/query rejected {} / {}: {} {}", carrier, trackNumber,
                    status, json.path("msg").asText(""));
            return Optional.empty();
        }
        JsonNode data = json.path("data");
        JsonNode list = data.path("list");
        if (!list.isArray()) {
            list = data.isArray() ? data : null;
        }
        if (list == null || list.isEmpty()) {
            return Optional.empty(); // no trace yet — a clean miss
        }
        List<ExpressTrackingSnapshot.Event> events = new ArrayList<>();
        for (JsonNode entry : list) {
            String time = firstText(entry, "time", "acceptTime", "ftime");
            String text = firstText(entry, "status", "content", "context", "acceptStation");
            events.add(new ExpressTrackingSnapshot.Event(time, null, text));
        }
        // OnePass lists newest-first; the first entry's text doubles as the current status.
        String currentStatus = firstText(data, "state", "status");
        if (currentStatus == null) {
            currentStatus = events.get(0).getDescription();
        }
        return Optional.of(new ExpressTrackingSnapshot(carrier, trackNumber, currentStatus, events));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.path(field);
            if (v.isValueNode() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
