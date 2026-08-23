package org.linlinjava.litemall.gatewayapi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.gatewayapi.web.seo.CategoryMeta;
import org.linlinjava.litemall.gatewayapi.web.seo.GoodsMeta;
import org.linlinjava.litemall.gatewayapi.web.seo.MetaLookup;
import org.linlinjava.litemall.gatewayapi.web.seo.PageMeta;
import org.linlinjava.litemall.gatewayapi.web.seo.SeoHeadRenderer;
import org.linlinjava.litemall.gatewayapi.web.seo.SeoMetaSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Wave-13 seam behaviour of {@link SpaHistoryFallbackFilter}: which navigations
 * get an injected head, and that every degraded path falls open to the plain
 * {@code /index.html} rewrite the filter has always done. The meta layer is a
 * hand-rolled {@link SeoMetaSource} stub — the inherited test classpath's
 * Mockito predates class mocking on this JDK.
 */
class SpaHistoryFallbackFilterSeoTest {

    private static final String SHELL = """
            <html><head><meta name="description" content="d" /><title>Trovemo</title></head>\
            <body><div id="root"></div></body></html>""";

    /** Programmable stub; also records whether the filter asked it anything. */
    private static final class StubMetaSource implements SeoMetaSource {
        /** Default: the lookup could not be made — the pre-MetaLookup behaviour. */
        Mono<MetaLookup<GoodsMeta>> goods = Mono.just(MetaLookup.unavailable());
        Mono<MetaLookup<CategoryMeta>> category = Mono.just(MetaLookup.unavailable());
        Mono<MetaLookup<PageMeta>> page = Mono.just(MetaLookup.unavailable());
        Mono<MetaLookup<PageMeta>> season = Mono.just(MetaLookup.unavailable());
        boolean queried;

        @Override
        public Mono<MetaLookup<GoodsMeta>> goodsMeta(String goodsId) {
            queried = true;
            return goods;
        }

        @Override
        public Mono<MetaLookup<CategoryMeta>> category(String categoryId) {
            queried = true;
            return category;
        }

        @Override
        public Mono<MetaLookup<PageMeta>> pageMeta(String pageId) {
            queried = true;
            return page;
        }

        @Override
        public Mono<MetaLookup<PageMeta>> seasonPage() {
            queried = true;
            return season;
        }
    }

    private StubMetaSource metaSource;
    private SpaHistoryFallbackFilter filter;

    /** The exchange the next filter saw, or null when the filter wrote the response itself. */
    private ServerWebExchange forwarded;
    private final WebFilterChain chain = exchange -> {
        forwarded = exchange;
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        metaSource = new StubMetaSource();
        filter = new SpaHistoryFallbackFilter(
                metaSource, new SeoHeadRenderer("https://trovemo.com", () -> SHELL));
        forwarded = null;
    }

    private MockServerWebExchange navigate(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("Accept", "text/html").build());
    }

    private static GoodsMeta meta() {
        return new GoodsMeta("123", "Vintage Denim Jacket", "brief", null, "39.99", "USD", true, null, null, null, null);
    }

    @Test
    @DisplayName("bare-id product navigation gets the injected head, not a rewrite")
    void productBareId() {
        metaSource.goods = Mono.just(MetaLookup.found(meta()));
        MockServerWebExchange exchange = navigate("/product/123");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .startsWith("text/html");
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("<title>Vintage Denim Jacket | Trovemo</title>")
                .contains("https://trovemo.com/product/123-vintage-denim-jacket");
    }

    @Test
    @DisplayName("slugged product URL resolves the id from the leading digits")
    void productSlugged() {
        metaSource.goods = Mono.just(MetaLookup.found(meta()));
        MockServerWebExchange exchange = navigate("/product/123-vintage-denim-jacket");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Vintage Denim Jacket | Trovemo");
    }

    @Test
    @DisplayName("meta unavailable (timeout/dead service ⇒ empty) falls open to the plain shell")
    void productMetaEmpty() {
        metaSource.goods = Mono.just(MetaLookup.unavailable());
        filter.filter(navigate("/product/123"), chain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("an unexpected meta error still falls open, never 5xx")
    void productMetaError() {
        metaSource.goods = Mono.error(new IllegalStateException("boom"));
        filter.filter(navigate("/product/123"), chain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("legacy cj_<pid> product routes are plain rewrites — no meta fetch")
    void productCjId() {
        filter.filter(navigate("/product/cj_98765"), chain).block();

        assertThat(metaSource.queried).isFalse();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("search navigation gets a noindex shell without any meta fetch")
    void searchNoindex() {
        MockServerWebExchange exchange = navigate("/search?q=dog+toy");
        filter.filter(exchange, chain).block();

        assertThat(metaSource.queried).isFalse();
        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("<meta name=\"robots\" content=\"noindex\"")
                .contains("<title>Trovemo</title>");
    }

    @Test
    @DisplayName("category navigation gets a name-driven head")
    void category() {
        metaSource.category = Mono.just(MetaLookup.found(new CategoryMeta("1036007", "Women's Clothing", 42)));
        MockServerWebExchange exchange = navigate("/category/1036007");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Women&#39;s Clothing | Trovemo");
    }

    @Test
    @DisplayName("Wave-20: active DIY page navigation gets the injected og-meta head")
    void pageActive() {
        metaSource.page = Mono.just(MetaLookup.found(new PageMeta("42", "Coupon spotlight", "/_cdn/cf/pic/hero.jpg")));
        MockServerWebExchange exchange = navigate("/page/42");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("<title>Coupon spotlight | Trovemo</title>")
                .contains("<link rel=\"canonical\" href=\"https://trovemo.com/page/42\"")
                .contains("<meta property=\"og:site_name\" content=\"Trovemo\"")
                .contains("<meta property=\"og:image\" content=\"https://trovemo.com/_cdn/cf/pic/hero.jpg\"");
    }

    @Test
    @DisplayName("Wave-20: missing/not-active page (empty meta) falls open to the plain shell")
    void pageMetaEmpty() {
        metaSource.page = Mono.just(MetaLookup.unavailable());
        filter.filter(navigate("/page/42"), chain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("Wave-20: a page meta error still falls open, never 5xx")
    void pageMetaError() {
        metaSource.page = Mono.error(new IllegalStateException("boom"));
        filter.filter(navigate("/page/42"), chain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("Wave-20: non-numeric page segment is a plain rewrite, no meta fetch")
    void pageNonNumeric() {
        filter.filter(navigate("/page/about-us"), chain).block();

        assertThat(metaSource.queried).isFalse();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("other SPA routes keep the plain rewrite")
    void otherSpaRoute() {
        filter.filter(navigate("/cart"), chain).block();

        assertThat(metaSource.queried).isFalse();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    @Test
    @DisplayName("asset and API paths are untouched, as before")
    void nonNavigationsUntouched() {
        filter.filter(navigate("/app/main.abc123.js"), chain).block();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/app/main.abc123.js");

        filter.filter(navigate("/srv/goods/detail"), chain).block();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/srv/goods/detail");
    }

    @Test
    @DisplayName("unbuilt tree (no shell template) ⇒ product routes fall back to the rewrite")
    void rendererUnavailable() {
        SeoHeadRenderer unbuilt = new SeoHeadRenderer("https://trovemo.com", () -> {
            throw new IOException("no built shell");
        });
        SpaHistoryFallbackFilter bare = new SpaHistoryFallbackFilter(metaSource, unbuilt);
        metaSource.goods = Mono.just(MetaLookup.found(meta()));

        bare.filter(navigate("/product/123"), chain).block();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }

    // ---- missing URLs answer 404, outages never do ------------------------

    @Test
    @DisplayName("a product goods-management reports gone: 404 with a noindex shell")
    void productAbsentIs404() {
        metaSource.goods = Mono.just(MetaLookup.missing());
        MockServerWebExchange exchange = navigate("/product/99999999");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("<meta name=\"robots\" content=\"noindex\"")
                // the body still hydrates, so a mistyped id shows the storefront
                .contains("<div id=\"root\"></div>");
    }

    @Test
    @DisplayName("a category that does not exist: 404; one the narrowing emptied: 200 noindex")
    void categoryAbsentVersusEmpty() {
        metaSource.category = Mono.just(MetaLookup.missing());
        MockServerWebExchange missing = navigate("/category/999999");
        filter.filter(missing, chain).block();
        assertThat(missing.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        metaSource.category = Mono.just(MetaLookup.found(new CategoryMeta("1005000", "home", 0)));
        MockServerWebExchange emptied = navigate("/category/1005000");
        filter.filter(emptied, chain).block();
        assertThat(emptied.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emptied.getResponse().getBodyAsString().block())
                .contains("<meta name=\"robots\" content=\"noindex\"");
    }

    @Test
    @DisplayName("an outage must NOT 404 — that would de-index the whole catalogue")
    void outageStays200() {
        metaSource.goods = Mono.just(MetaLookup.unavailable());
        MockServerWebExchange exchange = navigate("/product/123");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- HEAD ------------------------------------------------------------

    @Test
    @DisplayName("HEAD of a product: same status and headers as GET, no body")
    void headCarriesNoBody() {
        metaSource.goods = Mono.just(MetaLookup.found(meta()));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.head("/product/123").header("Accept", "text/html").build());
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString()).startsWith("text/html");
        // Content-Length still describes what the matching GET would return.
        assertThat(exchange.getResponse().getHeaders().getContentLength()).isGreaterThan(0);
        assertThat(exchange.getResponse().getBodyAsString().block()).isNullOrEmpty();
    }

    // ---- homepage --------------------------------------------------------

    @Test
    @DisplayName("home gets its own head: canonical, og:url and Organization identity")
    void homeInjected() {
        MockServerWebExchange exchange = navigate("/");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(metaSource.queried).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("<link rel=\"canonical\" href=\"https://trovemo.com/\"")
                .contains("<meta property=\"og:url\" content=\"https://trovemo.com/\"")
                .contains("\"@type\":\"Organization\"");
    }

    @Test
    @DisplayName("home with no built shell falls through to the gateway route untouched")
    void homeWithoutTemplate() {
        SpaHistoryFallbackFilter unbuilt = new SpaHistoryFallbackFilter(metaSource,
                new SeoHeadRenderer("https://trovemo.com", () -> {
                    throw new IOException("no webapp build");
                }));
        MockServerWebExchange exchange = navigate("/");
        unbuilt.filter(exchange, chain).block();

        assertThat(forwarded).isNotNull();
        // Not rewritten either — "/" is the gateway's own route.
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/");
    }

    // ---- season ----------------------------------------------------------

    @Test
    @DisplayName("/summer redirects to the active season page, temporarily")
    void seasonRedirect() {
        metaSource.season = Mono.just(MetaLookup.found(new PageMeta("5", "Autumns Deal", null)));
        MockServerWebExchange exchange = navigate("/summer");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        // FOUND, not MOVED_PERMANENTLY: the target changes every season.
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString("/page/5");
    }

    @Test
    @DisplayName("/summer with no season running redirects home, as the SPA does")
    void seasonRedirectHome() {
        metaSource.season = Mono.just(MetaLookup.missing());
        MockServerWebExchange exchange = navigate("/summer");
        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString("/");
    }

    @Test
    @DisplayName("/summer falls open to the shell when the season cannot be resolved")
    void seasonUnavailable() {
        metaSource.season = Mono.just(MetaLookup.unavailable());
        filter.filter(navigate("/summer"), chain).block();

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }
}
