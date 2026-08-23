package org.linlinjava.litemall.gatewayapi.web;

import org.linlinjava.litemall.gatewayapi.web.seo.CategoryMeta;
import org.linlinjava.litemall.gatewayapi.web.seo.MetaLookup;
import org.linlinjava.litemall.gatewayapi.web.seo.PageMeta;
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
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
 * <p><b>Wave-13 head injection.</b> Product ({@code /product/<id>[-slug]}),
 * category ({@code /category/<id>}) and — since Wave 20 — DIY-page
 * ({@code /page/<id>}) navigations are answered with the same shell
 * but a real head (title, canonical, OpenGraph, JSON-LD) rendered by
 * {@link SeoHeadRenderer} from goods-management meta — that is what no-JS social
 * crawlers and Google's first-wave fetch index. Strictly fail-open: meta not
 * fetched within {@code SeoMetaClient.FETCH_BUDGET}, a non-numeric id (legacy
 * {@code cj_<pid>} routes), an unbuilt webapp tree, or any error falls back to
 * the plain rewrite below. The HTML is identical for every caller of a given
 * URL (no UA cloaking), and marked {@code Cache-Control: no-cache} so no edge
 * cache can serve one product's head for another.
 *
 * <p><b>Missing URLs answer 404.</b> A route whose row goods-management reports
 * gone — an id that never existed, a deleted product, a category that is not
 * there — is served the shell with a {@code noindex} head and a 404 status,
 * instead of the generic 200 shell that Search Console files as a soft 404. The
 * distinction comes from {@link MetaLookup}: only an in-contract "not found"
 *answer counts, so an outage still falls open to 200 and the catalogue cannot be
 * de-indexed by a service being down.
 *
 * <p><b>HEAD is served like GET.</b> Crawlers, link checkers and uptime probes
 * use it; answering 401 to a HEAD of a public page made the whole site look
 * broken to every tool that asks that way. The response carries identical
 * status and headers and no body, per HTTP semantics.
 */
@Component
@Order(10)
public class SpaHistoryFallbackFilter implements WebFilter, Ordered {

    /**
     * The homepage description. It lived in the built shell and still advertised
     * "fashion, electronics, home, toys" months after the catalogue was narrowed
     * to the home/garden/DIY anchor; the template's copy is corrected too, and
     * this is the value the injected head states.
     */
    private static final String HOME_DESCRIPTION =
            "Trovemo — home, garden and DIY essentials. Lighting, storage, tools and "
                    + "outdoor kit, shipped to your door across Europe.";

    private static final List<String> API_PREFIXES =
            List.of("/srv/", "/auth/", "/actuator/", "/_cdn/");

    /** Leading digits are the goods id; an optional -slug tail is ignored. */
    private static final Pattern PRODUCT_ROUTE = Pattern.compile("^/product/(\\d+)(?:-[^/]*)?$");
    private static final Pattern CATEGORY_ROUTE = Pattern.compile("^/category/(\\d+)$");
    /** Wave-20 DIY promo pages — /page/<id> gets an og-meta head like products do. */
    private static final Pattern PAGE_ROUTE = Pattern.compile("^/page/(\\d+)$");
    /**
     * Wave-27 season entry. The SPA resolves this client-side and navigates on;
     * answering the redirect here means a crawler is handed the real season page
     * instead of a shell it must run JS to leave. 302, not 301: the target
     * genuinely changes when an admin activates the next season, and a cached
     * permanent redirect would strand visitors on last season's page.
     */
    private static final String SEASON_ROUTE = "/summer";

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
        String path = request.getURI().getRawPath();
        if (isHome(path) && isReadNavigation(request)) {
            // "/" is routed to index.html by the gateway's `frontend` route, so
            // it never reached the rewrite below and was the one crawlable page
            // with no head of its own.
            // Resolved synchronously and branched BEFORE writing: writeHtml
            // returns a Mono<Void> whose completion is empty, so composing this
            // with switchIfEmpty would run the chain again after a successful
            // write (the trap seoDocument's javadoc calls out).
            Optional<String> home = seoHeadRenderer.available()
                    ? seoHeadRenderer.renderHome(HOME_DESCRIPTION)
                    : Optional.empty();
            return home.isPresent()
                    ? writeHtml(exchange, home.get(), HttpStatus.OK)
                    : chain.filter(exchange);
        }
        if (isSpaNavigation(request)) {
            if (SEASON_ROUTE.equals(path)) {
                return seasonRedirect(exchange, chain);
            }
            return seoDocument(path)
                    .flatMap(doc -> doc.html().isEmpty()
                            ? serveShell(exchange, chain)
                            : writeHtml(exchange, doc.html(), doc.status()));
        }
        return chain.filter(exchange);
    }

    /**
     * What to answer for a crawlable route: the document to write and the status
     * it carries. An empty body means "serve the plain shell" — see
     * {@link #seoDocument(String)}.
     */
    private record SeoDocument(String html, HttpStatus status) {
        static final SeoDocument SHELL = new SeoDocument("", HttpStatus.OK);

        static SeoDocument ok(String html) {
            return new SeoDocument(html, HttpStatus.OK);
        }

        static SeoDocument notFound(String html) {
            return html.isEmpty() ? SHELL : new SeoDocument(html, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * The document for a crawlable route: an injected head when the row is
     * there, a noindex shell with 404 when the service says it is not, and the
     * plain shell whenever the answer could not be had.
     */
    private Mono<SeoDocument> seoDocument(String path) {
        if (!seoHeadRenderer.available()) {
            return Mono.just(SeoDocument.SHELL);
        }
        Matcher product = PRODUCT_ROUTE.matcher(path);
        if (product.matches()) {
            return render(seoMetaClient.goodsMeta(product.group(1)), seoHeadRenderer::renderProduct);
        }
        Matcher category = CATEGORY_ROUTE.matcher(path);
        if (category.matches()) {
            return render(seoMetaClient.category(category.group(1)), seoHeadRenderer::renderCategory);
        }
        // Wave-20 DIY pages: active pages get a name/og:image head; a draft or
        // missing page is reported absent and answers 404 — neither belongs in
        // the index, and a draft is not yet a page.
        Matcher page = PAGE_ROUTE.matcher(path);
        if (page.matches()) {
            return render(seoMetaClient.pageMeta(page.group(1)), seoHeadRenderer::renderPage);
        }
        // Internal search results: every ?q= spelling is a distinct URL over the
        // same shell — noindex keeps them out of the index (crawl-budget bloat).
        if ("/search".equals(path)) {
            return Mono.just(SeoDocument.ok(seoHeadRenderer.renderNoindex().orElse("")));
        }
        return Mono.just(SeoDocument.SHELL);
    }

    /** The one place the three lookup answers become three HTTP answers. */
    private <T> Mono<SeoDocument> render(Mono<MetaLookup<T>> lookup,
                                         Function<T, Optional<String>> renderer) {
        return lookup
                .map(result -> {
                    if (result.isFound()) {
                        return renderer.apply(result.value())
                                .map(SeoDocument::ok)
                                .orElse(SeoDocument.SHELL);
                    }
                    if (result.absent()) {
                        return SeoDocument.notFound(seoHeadRenderer.renderNotFound().orElse(""));
                    }
                    return SeoDocument.SHELL;
                })
                .defaultIfEmpty(SeoDocument.SHELL)
                .onErrorReturn(SeoDocument.SHELL);
    }

    /**
     * Hands /summer to the season page the SPA would navigate to anyway, or to
     * the storefront home when no season is running — the same two destinations
     * {@code SeasonRedirect.tsx} picks, decided server-side so a crawler never
     * has to run JS to find them. A lookup that fails falls open to the shell.
     */
    private Mono<Void> seasonRedirect(ServerWebExchange exchange, WebFilterChain chain) {
        return seoMetaClient.seasonPage()
                .flatMap(result -> {
                    if (result.isFound()) {
                        return redirect(exchange, "/page/" + result.value().id());
                    }
                    if (result.absent()) {
                        return redirect(exchange, "/");
                    }
                    return serveShell(exchange, chain);
                })
                .onErrorResume(e -> serveShell(exchange, chain));
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        response.getHeaders().setCacheControl("no-cache");
        return response.setComplete();
    }

    private Mono<Void> serveShell(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate().path("/index.html").build())
                .build());
    }

    private Mono<Void> writeHtml(ServerWebExchange exchange, String html, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        response.setStatusCode(status);
        response.getHeaders().setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        response.getHeaders().setCacheControl("no-cache");
        response.getHeaders().setContentLength(bytes.length);
        // A HEAD gets the same status and headers, and no body — writing one
        // would be a protocol violation, and Content-Length must still describe
        // the body the matching GET would return.
        if (HttpMethod.HEAD.equals(exchange.getRequest().getMethod())) {
            return response.setComplete();
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private static boolean isHome(String path) {
        return path.isEmpty() || "/".equals(path);
    }

    /** GET and HEAD are the same read; HEAD simply stops before the body. */
    private boolean isReadNavigation(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        return (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method))
                && acceptsHtml(request);
    }

    private boolean isSpaNavigation(ServerHttpRequest request) {
        if (!isReadNavigation(request)) {
            return false;
        }
        String path = request.getURI().getRawPath();
        if (isHome(path)) {
            return false;
        }
        String withSlash = path.endsWith("/") ? path : path + "/";
        for (String prefix : API_PREFIXES) {
            if (withSlash.startsWith(prefix)) {
                return false;
            }
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.indexOf('.') < 0;
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