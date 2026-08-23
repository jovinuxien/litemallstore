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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fetches the Wave-13 SEO meta a crawlable shell needs — product meta from
 * {@code GET /srv/goods/meta/{id}} and the category name from the existing
 * {@code GET /srv/search/category/{id}?size=1} landing read (the same call
 * CategoryTree.tsx makes), plus Wave-20 DIY-page meta from
 * {@code GET /srv/page/{id}} — from goods-management over the load balancer,
 * authenticated with the same machine token {@code MachineTokenRelayFilter}
 * relays for proxied public reads.
 *
 * <p><b>Fail-open is the whole design.</b> Head injection sits on the SPA
 * navigation path, so a slow or dead goods service must cost a deep link at
 * most {@link #FETCH_BUDGET} before it gets the plain shell it gets today.
 * Every public method answers a {@link MetaLookup} and never completes with an
 * error: a timeout, connection failure or unparseable body is
 * {@code unavailable} (serve the plain shell, exactly as before), while a
 * non-zero errno envelope is {@code absent} (the row really is gone, and the
 * caller may answer 404).
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

    /** Product meta; absent only when goods-management says the row is gone. */
    @Override
    public Mono<MetaLookup<GoodsMeta>> goodsMeta(String goodsId) {
        return lookup("goods:" + goodsId,
                () -> fetch("/srv/goods/meta/{id}", goodsId),
                body -> parseGoods(goodsId, body));
    }

    /** Category name + product count; absent on the service's own "not found". */
    @Override
    public Mono<MetaLookup<CategoryMeta>> category(String categoryId) {
        return lookup("category:" + categoryId,
                () -> fetch("/srv/search/category/{id}?size=1", categoryId),
                body -> parseCategory(categoryId, body));
    }

    /**
     * Wave-20 DIY-page meta from the public {@code GET /srv/page/{id}} read.
     * The endpoint serves ACTIVE pages only, so a draft or missing page answers
     * a non-zero errno and arrives here as absent — correct for both: neither
     * is a page the index should hold.
     */
    @Override
    public Mono<MetaLookup<PageMeta>> pageMeta(String pageId) {
        return lookup("page:" + pageId,
                () -> fetch("/srv/page/{id}", pageId),
                body -> parsePage(pageId, body));
    }

    /**
     * The active season page (Wave 27). Errno 642 — no season running — is a
     * normal state, not a failure, and arrives as absent.
     */
    @Override
    public Mono<MetaLookup<PageMeta>> seasonPage() {
        return lookup("page:season",
                () -> fetch("/srv/page/season"),
                body -> parsePage(null, body));
    }

    /**
     * One fetch, classified into the three answers.
     *
     * <p>Everything that is not an in-contract errno envelope stays
     * "unavailable", which the caller treats exactly as this client behaved
     * before {@link MetaLookup} existed: serve the plain shell, 200. Only a
     * body that parsed as an envelope AND carried a non-zero errno is reported
     * absent, because that is the service positively stating the row is not
     * there. An envelope we cannot parse is NOT absence — a contract drift
     * must degrade to the old behaviour, never to a site-wide 404.
     */
    private <T> Mono<MetaLookup<T>> lookup(String key,
                                           Supplier<Mono<JsonNode>> fetch,
                                           Function<JsonNode, T> parser) {
        return cached(key, fetch)
                .timeout(FETCH_BUDGET)
                .map(body -> classify(body, parser))
                .onErrorReturn(MetaLookup.unavailable())
                .defaultIfEmpty(MetaLookup.unavailable());
    }

    private static <T> MetaLookup<T> classify(JsonNode body, Function<JsonNode, T> parser) {
        if (body == null || !body.hasNonNull("errno")) {
            return MetaLookup.unavailable();
        }
        if (body.path("errno").asInt(-1) != 0) {
            return MetaLookup.missing();
        }
        T value = parser.apply(body);
        return value == null ? MetaLookup.unavailable() : MetaLookup.found(value);
    }

    private Mono<JsonNode> cached(String key, Supplier<Mono<JsonNode>> fetch) {
        if (cache.size() > CACHE_MAX) {
            cache.clear();
        }
        return cache.computeIfAbsent(key, k ->
                fetch.get().cache(value -> HIT_TTL, error -> MISS_TTL, () -> MISS_TTL));
    }

    /** Varargs so the season read, which takes no path variable, uses the same path. */
    private Mono<JsonNode> fetch(String uriTemplate, Object... uriVariables) {
        return machineToken().flatMap(token -> webClient.get()
                .uri(uriTemplate, uriVariables)
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
                d.hasNonNull("reviewCount") ? d.path("reviewCount").asInt() : null,
                text(d, "categoryId"),
                text(d, "categoryName"));
    }

    /**
     * The category landing payload nests the current node as {@code data.category}
     * with the breadcrumb alongside; older shapes carried the name only on the
     * breadcrumb's last element, so both are read.
     */
    private static CategoryMeta parseCategory(String categoryId, JsonNode body) {
        JsonNode d = body.path("data");
        String name = text(d.path("category"), "name");
        if (name == null) {
            JsonNode crumbs = d.path("breadcrumb");
            if (crumbs.isArray() && crumbs.size() > 0) {
                name = text(crumbs.get(crumbs.size() - 1), "name");
            }
        }
        // `total` is the count behind the landing; a real category that the
        // narrowing emptied reports 0 and must be rendered noindex, not 404.
        return name == null ? null : new CategoryMeta(categoryId, name, d.path("total").asInt(0));
    }

    private static PageMeta parsePage(String pageId, JsonNode body) {
        JsonNode d = body.path("data");
        if (!d.hasNonNull("name") || d.path("name").asText().isBlank()) {
            return null;
        }
        String id = d.hasNonNull("id") ? d.path("id").asText() : pageId;
        if (id == null) {
            return null;
        }
        return new PageMeta(
                id,
                d.path("name").asText(),
                firstComponentImage(d.path("components")));
    }

    /**
     * The first image-bearing component's URL, in configured order: a banner's
     * {@code config.image}, else the first usable entry of an image-row's
     * {@code config.images[]}. Other palette components carry no image in
     * config; {@code null} when nothing usable exists.
     */
    private static String firstComponentImage(JsonNode components) {
        if (!components.isArray()) {
            return null;
        }
        for (JsonNode component : components) {
            JsonNode config = component.path("config");
            String image = text(config, "image");
            if (image != null) {
                return image;
            }
            JsonNode images = config.path("images");
            if (images.isArray()) {
                for (JsonNode entry : images) {
                    String rowImage = text(entry, "image");
                    if (rowImage != null) {
                        return rowImage;
                    }
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank()
                ? node.path(field).asText()
                : null;
    }
}
