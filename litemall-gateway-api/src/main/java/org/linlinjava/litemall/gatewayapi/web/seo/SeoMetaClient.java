package org.linlinjava.litemall.gatewayapi.web.seo;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancedExchangeFilterFunction;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Fetches the Wave-13 SEO meta a crawlable shell needs — product meta from
 * {@code GET /srv/goods/meta/{id}} and the category name from the existing
 * {@code GET /srv/search/category/{id}?size=1} landing read (the same call
 * CategoryTree.tsx makes) — from goods-management over the load balancer,
 * authenticated with the same machine token {@code MachineTokenRelayFilter}
 * relays for proxied public reads.
 *
 * <p><b>Fail-open is the whole design.</b> Head injection sits on the SPA
 * navigation path, so a slow or dead goods service must cost a deep link at
 * most {@link #FETCH_BUDGET} before it gets the plain shell it gets today.
 * Every public method completes empty on timeout, connection failure, a
 * non-zero errno envelope, or an unparseable body — never with an error.
 *
 * <p>Results are memoised per id as TTL-cached {@link Mono}s (hits 5&nbsp;min,
 * misses/errors 60&nbsp;s so a booting service is retried soon). The cache also
 * de-duplicates concurrent fetches for the same id, and because the timeout is
 * applied per-subscriber on the <em>cached</em> mono, a first hit that misses
 * the budget still completes the fetch in the background and warms the cache
 * for the next visitor. The catalog is ~10k goods; the size guard is a
 * runaway-protection reset, not an LRU.
 */
@Component
public class SeoMetaClient implements SeoMetaSource {

    static final Duration FETCH_BUDGET = Duration.ofMillis(300);
    private static final Duration HIT_TTL = Duration.ofMinutes(5);
    private static final Duration MISS_TTL = Duration.ofSeconds(60);
    private static final int CACHE_MAX = 20_000;

    private final WebClient webClient;
    private final ReactiveOAuth2AuthorizedClientManager clientManager;
    private final Map<String, Mono<JsonNode>> cache = new ConcurrentHashMap<>();

    public SeoMetaClient(WebClient.Builder webClientBuilder,
                         LoadBalancedExchangeFilterFunction loadBalancer,
                         ReactiveOAuth2AuthorizedClientManager machineAuthorizedClientManager,
                         @Value("${litemall.seo.meta-base-url:lb://litemall-goods-management}") String baseUrl) {
        // lb:// goes through the load balancer (prod/dev default); a plain
        // http:// base URL skips it so tests can point at a local stub.
        if (baseUrl.startsWith("lb://")) {
            this.webClient = webClientBuilder
                    .baseUrl("http://" + baseUrl.substring("lb://".length()))
                    .filter(loadBalancer)
                    .build();
        } else {
            this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        }
        this.clientManager = machineAuthorizedClientManager;
    }

    /** Product meta, or empty (never an error) when it cannot be had in budget. */
    @Override
    public Mono<GoodsMeta> goodsMeta(String goodsId) {
        return cached("goods:" + goodsId, () -> fetch("/srv/goods/meta/{id}", goodsId))
                .timeout(FETCH_BUDGET)
                .onErrorResume(e -> Mono.empty())
                .flatMap(body -> Mono.justOrEmpty(parseGoods(goodsId, body)));
    }

    /** Category display name, or empty (never an error). */
    @Override
    public Mono<String> categoryName(String categoryId) {
        return cached("category:" + categoryId, () -> fetch("/srv/search/category/{id}?size=1", categoryId))
                .timeout(FETCH_BUDGET)
                .onErrorResume(e -> Mono.empty())
                .flatMap(body -> Mono.justOrEmpty(parseCategoryName(body)));
    }

    private Mono<JsonNode> cached(String key, Supplier<Mono<JsonNode>> fetch) {
        if (cache.size() > CACHE_MAX) {
            cache.clear();
        }
        return cache.computeIfAbsent(key, k ->
                fetch.get().cache(value -> HIT_TTL, error -> MISS_TTL, () -> MISS_TTL));
    }

    private Mono<JsonNode> fetch(String uriTemplate, String id) {
        return machineToken().flatMap(token -> webClient.get()
                .uri(uriTemplate, id)
                .headers(h -> {
                    if (!token.isEmpty()) {
                        h.setBearerAuth(token);
                    }
                })
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class));
    }

    private Mono<String> machineToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId("authserver")
                .principal("gateway-api-machine")
                .build();
        return clientManager.authorize(request)
                .map(client -> client.getAccessToken().getTokenValue())
                .onErrorResume(e -> Mono.empty())
                // No token (authserver down) still attempts the read — the
                // downstream 401 then falls into the same fail-open path.
                .defaultIfEmpty("");
    }

    private static GoodsMeta parseGoods(String goodsId, JsonNode body) {
        if (body == null || body.path("errno").asInt(-1) != 0) {
            return null;
        }
        JsonNode d = body.path("data");
        if (!d.hasNonNull("name") || d.path("name").asText().isBlank()) {
            return null;
        }
        return new GoodsMeta(
                d.hasNonNull("id") ? d.path("id").asText() : goodsId,
                d.path("name").asText(),
                text(d, "brief"),
                text(d, "picUrl"),
                text(d, "retailPrice"),
                d.hasNonNull("currency") ? d.path("currency").asText() : null,
                d.path("onSale").asBoolean(true),
                text(d, "rating"),
                d.hasNonNull("reviewCount") ? d.path("reviewCount").asInt() : null);
    }

    /**
     * The category landing payload nests the current node as {@code data.category}
     * with the breadcrumb alongside; older shapes carried the name only on the
     * breadcrumb's last element, so both are read.
     */
    private static String parseCategoryName(JsonNode body) {
        if (body == null || body.path("errno").asInt(-1) != 0) {
            return null;
        }
        JsonNode d = body.path("data");
        String name = text(d.path("category"), "name");
        if (name == null) {
            JsonNode crumbs = d.path("breadcrumb");
            if (crumbs.isArray() && crumbs.size() > 0) {
                name = text(crumbs.get(crumbs.size() - 1), "name");
            }
        }
        return name;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank()
                ? node.path(field).asText()
                : null;
    }
}
