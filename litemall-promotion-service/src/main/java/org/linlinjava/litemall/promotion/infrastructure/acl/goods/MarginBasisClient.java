package org.linlinjava.litemall.promotion.infrastructure.acl.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Wave-18 margin-guard basis fetch: goods-management
 * {@code POST /srv/private/admin/insight/margin-basis} per the FROZEN handoff
 * spec {@code litemall-goods-management/docs/handoff-coupon-margin-basis.md}.
 * Auth = machine token + forwarded {@code X-User-Roles: ROLE_ADMIN} (the
 * order-service recipe for admin-prefixed sister-service paths).
 *
 * <p>ANY failure (transport, non-200, non-zero errno, unparseable body) is
 * surfaced as {@link MarginBasisUnavailableException} so the caller fails
 * CLOSED — a coupon is never saved unguarded.
 */
@Component
public class MarginBasisClient implements CouponMarginBasisPort {

    private static final Logger log = LoggerFactory.getLogger(MarginBasisClient.class);

    private final String goodsServiceUrl;
    private final GoodsMachineTokenProvider tokenProvider;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public MarginBasisClient(@Value("${goods.service.url:http://localhost:8082}") String goodsServiceUrl,
                             GoodsMachineTokenProvider tokenProvider) {
        this.goodsServiceUrl = goodsServiceUrl;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public MarginBasis fetch(List<Integer> goodsIds, List<Integer> categoryIds) {
        ObjectNode body = mapper.createObjectNode();
        body.set("goodsIds", toArray(goodsIds));
        body.set("categoryIds", toArray(categoryIds));

        String token;
        try {
            token = tokenProvider.getToken();
        } catch (Exception e) {
            throw new MarginBasisUnavailableException("machine token unavailable: " + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(goodsServiceUrl + "/srv/private/admin/insight/margin-basis"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", forwardedUserId())
                .header("X-User-Roles", "ROLE_ADMIN")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new MarginBasisUnavailableException(
                        "margin-basis returned HTTP " + resp.statusCode());
            }
            JsonNode envelope = mapper.readTree(resp.body());
            int errno = envelope.path("errno").asInt(-1);
            if (errno != 0) {
                throw new MarginBasisUnavailableException(
                        "margin-basis errno " + errno + ": " + envelope.path("errmsg").asText(""));
            }
            JsonNode data = envelope.path("data");
            return new MarginBasis(
                    data.path("onSaleCount").asInt(0),
                    data.path("costedCount").asInt(0),
                    data.path("uncostedCount").asInt(0),
                    decimalOrNull(data.get("maxCostRatio")),
                    decimalOrNull(data.get("minRetailPrice")));
        } catch (MarginBasisUnavailableException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("margin-basis call failed: {}", e.getMessage());
            throw new MarginBasisUnavailableException("margin-basis call failed: " + e.getMessage(), e);
        }
    }

    /**
     * svcsecurity's {@code MachineTokenUserContextFilter} swaps in the forwarded
     * user (with the {@code X-User-Roles} authorities) ONLY when {@code X-User-Id}
     * is present — a bare machine token keeps its role-less {@code JwtAuthenticationToken}
     * and the admin-gated path 403s. Forward the acting admin's id from the current
     * request when available (gateway-admin injects it), else a {@code "0"} service
     * sentinel so the roles header still takes effect.
     */
    private static String forwardedUserId() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                String userId = servletAttrs.getRequest().getHeader("X-User-Id");
                if (userId != null && !userId.isBlank()) {
                    return userId;
                }
            }
        } catch (Exception ignored) {
            // fall through to sentinel
        }
        return "0";
    }

    private ArrayNode toArray(List<Integer> ids) {
        ArrayNode array = mapper.createArrayNode();
        if (ids != null) {
            for (Integer id : ids) {
                if (id != null) {
                    array.add(id);
                }
            }
        }
        return array;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node == null || node.isNull() || !node.isNumber() ? null : node.decimalValue();
    }
}
