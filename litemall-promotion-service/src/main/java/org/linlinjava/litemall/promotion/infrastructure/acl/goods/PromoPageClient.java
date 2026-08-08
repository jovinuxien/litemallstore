package org.linlinjava.litemall.promotion.infrastructure.acl.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.promotion.application.ports.PromoPagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PromoPagePort} over goods-management's PUBLIC DIY-page read
 * ({@code GET /srv/page/{id}} on {@code goods.service.url}) — anonymous path,
 * so unlike {@link MarginBasisClient} there is no machine token to mint.
 *
 * <p>Envelope handling: errno 0 ⇒ page view; errno 642 (PAGE_NOT_ACTIVE, also
 * answered for unknown ids) ⇒ empty; anything else (transport, non-200, other
 * errno, unparseable body) ⇒ {@link PromoPageGatewayException} so the caller
 * degrades honestly instead of publishing a half-composed post.
 */
@Component
public class PromoPageClient implements PromoPagePort {

    private static final Logger log = LoggerFactory.getLogger(PromoPageClient.class);

    /** goods-management's GoodsServiceResponseCode.PAGE_NOT_ACTIVE. */
    private static final int ERRNO_PAGE_NOT_ACTIVE = 642;

    private final String goodsServiceUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public PromoPageClient(@Value("${goods.service.url:http://localhost:8082}") String goodsServiceUrl) {
        this.goodsServiceUrl = goodsServiceUrl;
    }

    @Override
    public Optional<PromoPage> fetchActive(Integer pageId) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(goodsServiceUrl + "/srv/page/" + pageId))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new PromoPageGatewayException("page read returned HTTP " + resp.statusCode());
            }
            JsonNode envelope = mapper.readTree(resp.body());
            int errno = envelope.path("errno").asInt(-1);
            if (errno == ERRNO_PAGE_NOT_ACTIVE) {
                return Optional.empty();
            }
            if (errno != 0) {
                throw new PromoPageGatewayException(
                        "page read errno " + errno + ": " + envelope.path("errmsg").asText(""));
            }
            return Optional.of(toPage(envelope.path("data")));
        } catch (PromoPageGatewayException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("page read failed for page {}: {}", pageId, e.getMessage());
            throw new PromoPageGatewayException("page read failed: " + e.getMessage(), e);
        }
    }

    private PromoPage toPage(JsonNode data) {
        Integer id = data.path("id").isInt() ? data.path("id").asInt() : null;
        String name = data.path("name").asText("");
        // Pre-V54 responses carry no category field — default to 'general'.
        String category = data.path("category").asText("general");
        List<PageComponent> components = new ArrayList<>();
        for (JsonNode component : data.path("components")) {
            Map<String, Object> config = component.hasNonNull("config")
                    ? mapper.convertValue(component.get("config"),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            })
                    : Map.of();
            components.add(new PageComponent(component.path("type").asText(""), config));
        }
        return new PromoPage(id, name, category, components);
    }
}
