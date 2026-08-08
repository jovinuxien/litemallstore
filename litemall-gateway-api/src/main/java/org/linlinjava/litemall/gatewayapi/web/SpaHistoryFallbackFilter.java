package org.linlinjava.litemall.gatewayapi.web;

import org.linlinjava.litemall.gatewayapi.web.seo.SeoHeadRenderer;
import org.linlinjava.litemall.gatewayapi.web.seo.SeoMetaSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * History-API fallback for the bundled customer SPA.
 *
 * <p>The SPA is a client-side-routed React app: every route in App.tsx
 * ({@code /cart}, {@code /checkout}, {@code /product/123}, …) exists only in the
 * browser. The gateway's route table serves {@code index.html} for {@code /} alone,
 * so any full page load of a deep link — a refresh on the cart, a bookmarked
 * product, a shared link, "open in new tab" — fell through the route table and the
 * static-resource handler to a Whitelabel 404. In dev this never showed because the
 * webpack dev-server's {@code historyApiFallback} owned the front door.
 *
 * <p>This filter rewrites those navigations to {@code /index.html} before handler
 * mapping, so the static-resource handler serves the shell and React Router takes
 * over client-side. {@link org.linlinjava.litemall.gatewayapi.security.PublicPaths}
 * already treats every non-API GET as the public SPA shell, so this changes what a
 * deep link RETURNS, not who may fetch it.
 *
 * <p>Scope guards — a rewrite here must never shadow real content:
 * <ul>
 *   <li>GET only, and only when the client accepts HTML (browser navigations do;
 *       XHR/fetch for JSON and asset loads do not).</li>
 *   <li>API and proxy prefixes are untouched: {@code /srv}, {@code /auth},
 *       {@code /actuator}, {@code /_cdn} — their 404s must stay 404s.</li>
 *   <li>Paths whose last segment contains a dot ({@code /app/main.abc123.js},
 *       {@code /favicon.ico}, {@code /manifest.webmanifest}) are asset lookups, not
 *       routes — a missing file must 404, not silently return HTML.</li>
 * </ul>
 *
 * <p>Ordered after Spring Security's chain (-100): authorization evaluates the real
 * requested path, then the rewrite decides what content answers it.
 *
 * <p><b>Wave-13 head injection.</b> Product ({@code /product/<id>[-slug]}) and
 * category ({@code /category/<id>}) navigations are answered with the same shell
 * but a real head (title, canonical, OpenGraph, JSON-LD) rendered by
 * {@link SeoHeadRenderer} from goods-management meta — that is what no-JS social
 * crawlers and Google's first-wave fetch index. Strictly fail-open: meta not
 * fetched within {@code SeoMetaClient.FETCH_BUDGET}, a non-numeric id (legacy
 * {@code cj_<pid>} routes), an unbuilt webapp tree, or any error falls back to
 * the plain rewrite below. The HTML is identical for every caller of a given
 * URL (no UA cloaking), and marked {@code Cache-Control: no-cache} so no edge
 * cache can serve one product's head for another.
 */
@Component
@Order(10)
public class SpaHistoryFallbackFilter implements WebFilter, Ordered {

    private static final List<String> API_PREFIXES =
            List.of("/srv/", "/auth/", "/actuator/", "/_cdn/");

    /** Leading digits are the goods id; an optional -slug tail is ignored. */
    private static final Pattern PRODUCT_ROUTE = Pattern.compile("^/product/(\\d+)(?:-[^/]*)?$");
    private static final Pattern CATEGORY_ROUTE = Pattern.compile("^/category/(\\d+)$");

    private final SeoMetaSource seoMetaClient;
    private final SeoHeadRenderer seoHeadRenderer;

    public SpaHistoryFallbackFilter(SeoMetaSource seoMetaClient, SeoHeadRenderer seoHeadRenderer) {
        this.seoMetaClient = seoMetaClient;
        this.seoHeadRenderer = seoHeadRenderer;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isSpaNavigation(request)) {
            return seoHtml(request.getURI().getRawPath())
                    .flatMap(html -> html.isEmpty()
                            ? serveShell(exchange, chain)
                            : writeHtml(exchange, html));
        }
        return chain.filter(exchange);
    }

    /**
     * The injected document for a crawlable route, or {@code ""} for "serve the
     * plain shell" — empty string rather than an empty Mono because the success
     * path writes a {@code Mono<Void>}, whose completion is indistinguishable
     * from emptiness under {@code switchIfEmpty}.
     */
    private Mono<String> seoHtml(String path) {
        if (!seoHeadRenderer.available()) {
            return Mono.just("");
        }
        Matcher product = PRODUCT_ROUTE.matcher(path);
        if (product.matches()) {
            return seoMetaClient.goodsMeta(product.group(1))
                    .flatMap(meta -> Mono.justOrEmpty(seoHeadRenderer.renderProduct(meta)))
                    .defaultIfEmpty("")
                    .onErrorReturn("");
        }
        Matcher category = CATEGORY_ROUTE.matcher(path);
        if (category.matches()) {
            return seoMetaClient.categoryName(category.group(1))
                    .flatMap(name -> Mono.justOrEmpty(
                            seoHeadRenderer.renderCategory(category.group(1), name)))
                    .defaultIfEmpty("")
                    .onErrorReturn("");
        }
        // Internal search results: every ?q= spelling is a distinct URL over the
        // same shell — noindex keeps them out of the index (crawl-budget bloat).
        if ("/search".equals(path)) {
            return Mono.just(seoHeadRenderer.renderNoindex().orElse(""));
        }
        return Mono.just("");
    }

    private Mono<Void> serveShell(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate().path("/index.html").build())
                .build());
    }

    private Mono<Void> writeHtml(ServerWebExchange exchange, String html) {
        ServerHttpResponse response = exchange.getResponse();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        response.getHeaders().setCacheControl("no-cache");
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private boolean isSpaNavigation(ServerHttpRequest request) {
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return false;
        }
        String path = request.getURI().getRawPath();
        // "/" is already routed to index.html by the `frontend` gateway route.
        if (path.isEmpty() || "/".equals(path)) {
            return false;
        }
        String withSlash = path.endsWith("/") ? path : path + "/";
        for (String prefix : API_PREFIXES) {
            if (withSlash.startsWith(prefix)) {
                return false;
            }
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        if (lastSegment.indexOf('.') >= 0) {
            return false;
        }
        return acceptsHtml(request);
    }

    private boolean acceptsHtml(ServerHttpRequest request) {
        try {
            for (MediaType accepted : request.getHeaders().getAccept()) {
                if (accepted.includes(MediaType.TEXT_HTML)) {
                    return true;
                }
            }
        } catch (RuntimeException malformedAcceptHeader) {
            return false;
        }
        return false;
    }
}