package org.linlinjava.litemall.gatewayadmin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.gatewayadmin.application.web.filter.SpaWebFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Which paths reach the SPA shell.
 *
 * <p>Written because {@code /admin/**} did NOT, and that broke every deep link into the admin
 * console: the React router is mounted at {@code /admin/*}, so a bookmark, an F5, or a link
 * opened in a new tab hit the gateway instead of {@code index.html}, matched no route, and came
 * back as a bare 401 — which the browser showed as a login screen. Reported from the SEO title
 * page, whose product links open in a new tab; the cause was never that page.
 *
 * <p>The filter had no tests at all, which is why a one-word list could quietly decide that.
 */
public class SpaWebFilterTest {

    private final SpaWebFilter filter = new SpaWebFilter();

    /** @return the path the chain actually saw — i.e. after any rewrite */
    private String pathAfterFilter(String requested) {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get(requested).build());
        AtomicReference<String> seen = new AtomicReference<>();
        WebFilterChain chain = (ServerWebExchange ex) -> {
            seen.set(ex.getRequest().getURI().getPath());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return seen.get();
    }

    // ---------------- the regression ----------------

    @Test
    public void adminDeepLinksReachTheSpaShell() {
        // The exact link the SEO title page renders.
        assertEquals("/index.html", pathAfterFilter("/admin/goods/10000844/edit"));
        // ...and the rest of the console, which had the same problem.
        assertEquals("/index.html", pathAfterFilter("/admin"));
        assertEquals("/index.html", pathAfterFilter("/admin/dashboard"));
        assertEquals("/index.html", pathAfterFilter("/admin/goods/seo-titles"));
        assertEquals("/index.html", pathAfterFilter("/admin/mall/order/123"));
    }

    // ---------------- what must NOT be rewritten ----------------

    @Test
    public void backendPrefixesArePassedThroughUntouched() {
        // Every admin API path is /srv/private/admin/** — those must still reach the gateway,
        // which is the whole reason removing "/admin" from the list is safe.
        assertEquals("/srv/private/admin/seo/titles", pathAfterFilter("/srv/private/admin/seo/titles"));
        assertEquals("/srv/private/admin/order/list", pathAfterFilter("/srv/private/admin/order/list"));
        assertEquals("/auth/login", pathAfterFilter("/auth/login"));
        assertEquals("/actuator/health", pathAfterFilter("/actuator/health"));
        assertEquals("/management/info", pathAfterFilter("/management/info"));
        assertEquals("/oauth2/token", pathAfterFilter("/oauth2/token"));
    }

    @Test
    public void staticAssetsArePassedThroughUntouched() {
        // Anything with a dot is an asset; rewriting it would serve HTML as JS.
        assertEquals("/main.d95ec5cd.js", pathAfterFilter("/main.d95ec5cd.js"));
        assertEquals("/content/main.css", pathAfterFilter("/content/main.css"));
        assertEquals("/favicon.ico", pathAfterFilter("/favicon.ico"));
        assertEquals("/index.html", pathAfterFilter("/index.html"));
    }

    @Test
    public void aPrefixIsMatchedOnSegmentBoundariesNotSubstrings() {
        // "/administration" must not be mistaken for the "/admin" family, in either direction.
        assertEquals("/index.html", pathAfterFilter("/administration"));
        // "/srvsomething" is not "/srv".
        assertEquals("/index.html", pathAfterFilter("/srvfoo"));
    }

    @Test
    public void theRootAndOrdinaryClientRoutesReachTheShell() {
        assertEquals("/index.html", pathAfterFilter("/"));
        assertEquals("/index.html", pathAfterFilter("/dashboard"));
    }
}
