package org.linlinjava.litemall.gatewayapi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.gatewayapi.web.seo.GoodsMeta;
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
        Mono<GoodsMeta> goods = Mono.empty();
        Mono<String> category = Mono.empty();
        Mono<PageMeta> page = Mono.empty();
        boolean queried;

        @Override
        public Mono<GoodsMeta> goodsMeta(String goodsId) {
            queried = true;
            return goods;
        }

        @Override
        public Mono<String> categoryName(String categoryId) {
            queried = true;
            return category;
        }

        @Override
        public Mono<PageMeta> pageMeta(String pageId) {
            queried = true;
            return page;
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
        return new GoodsMeta("123", "Vintage Denim Jacket", "brief", null, "39.99", "USD", true, null, null);
    }

    @Test
    @DisplayName("bare-id product navigation gets the injected head, not a rewrite")
    void productBareId() {
        metaSource.goods = Mono.just(meta());
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
        metaSource.goods = Mono.just(meta());
        MockServerWebExchange exchange = navigate("/product/123-vintage-denim-jacket");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Vintage Denim Jacket | Trovemo");
    }

    @Test
    @DisplayName("meta unavailable (timeout/dead service ⇒ empty) falls open to the plain shell")
    void productMetaEmpty() {
        metaSource.goods = Mono.empty();
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
        metaSource.category = Mono.just("Women's Clothing");
        MockServerWebExchange exchange = navigate("/category/1036007");
        filter.filter(exchange, chain).block();

        assertThat(forwarded).isNull();
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Women&#39;s Clothing | Trovemo");
    }

    @Test
    @DisplayName("Wave-20: active DIY page navigation gets the injected og-meta head")
    void pageActive() {
        metaSource.page = Mono.just(new PageMeta("42", "Coupon spotlight", "/_cdn/cf/pic/hero.jpg"));
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
        metaSource.page = Mono.empty();
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
        metaSource.goods = Mono.just(meta());

        bare.filter(navigate("/product/123"), chain).block();
        assertThat(forwarded.getRequest().getURI().getPath()).isEqualTo("/index.html");
    }
}
