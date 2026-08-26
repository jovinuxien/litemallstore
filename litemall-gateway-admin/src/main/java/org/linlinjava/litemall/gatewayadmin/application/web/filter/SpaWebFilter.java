package org.linlinjava.litemall.gatewayadmin.application.web.filter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Serves the embedded admin SPA in the default / prod profiles.
 *
 * <p>Client-side (HTML5 history) routes such as {@code /dashboard} or
 * {@code /goods/list} have no matching static file, so they are rewritten to
 * {@code /index.html} and the React router takes over. The built SPA lives on
 * the classpath ({@code classpath:/static}, produced by frontend-maven-plugin)
 * and is served by WebFlux static-resource handling.
 *
 * <p>Registered only when the {@code dev} profile is NOT active — in {@code dev}
 * the SPA is served by the webpack dev server (port 9000) and the gateway
 * proxies {@code /**} to it (see application.yml {@code frontend-dev} route).
 *
 * <p><b>Critical:</b> backend-routed prefixes are excluded so this filter never
 * rewrites a gateway route ({@code /srv/**}, {@code /admin/**}) or the local
 * {@code /auth/**} endpoints to {@code /index.html} before the gateway can
 * route them. Anything containing a {@code .} is treated as a static asset and
 * left untouched.
 */
@Component
@Profile("!dev")
public class SpaWebFilter implements WebFilter {

    /**
     * Prefixes owned by the backend (gateway routes, auth, actuator, docs).
     *
     * <p><b>{@code /admin} is deliberately NOT here.</b> It was, and that broke every deep
     * link into the admin console: the React router is mounted at {@code /admin/*}, so
     * {@code /admin/goods/123/edit} is a CLIENT route, but listing the prefix here meant it
     * was never rewritten to {@code /index.html}. The request fell through to the gateway,
     * matched no route, and the browser got a bare 401 with no HTML — so a bookmark, an F5,
     * or any link opened in a new tab landed on a login screen instead of the page.
     *
     * <p>Nothing backend answers on bare {@code /admin}: every admin API path is
     * {@code /srv/private/admin/**} (see the gateway routes in application.yml), and no
     * controller in this service maps {@code /admin}. The prefix was a leftover from the
     * legacy admin-api decommissioned in Wave 4.
     *
     * <p>Serving the shell here leaks nothing: {@code index.html} is public by design, the
     * SPA's PrivateRoute still guards what renders, and every byte of data still comes from
     * an authenticated {@code /srv/**} call. This is exactly how the storefront edge treats
     * its own routes.
     */
    private static final String[] BACKEND_PREFIXES = {
            "/srv", "/auth", "/api", "/management",
            "/actuator", "/fallback", "/v3/api-docs", "/swagger-ui",
            "/login", "/oauth2", "/services"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isSpaRoute(path)) {
            return chain.filter(exchange.mutate()
                    .request(exchange.getRequest().mutate().path("/index.html").build())
                    .build());
        }
        return chain.filter(exchange);
    }

    private static boolean isSpaRoute(String path) {
        if (path.contains(".")) {
            // static asset (foo.js, bar.css, favicon.ico, ...)
            return false;
        }
        for (String prefix : BACKEND_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return false;
            }
        }
        return true;
    }
}
