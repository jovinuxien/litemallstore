package org.linlinjava.litemall.gatewayapi.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

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
 */
@Component
@Order(10)
public class SpaHistoryFallbackFilter implements WebFilter, Ordered {

    private static final List<String> API_PREFIXES =
            List.of("/srv/", "/auth/", "/actuator/", "/_cdn/");

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isSpaNavigation(request)) {
            return chain.filter(exchange.mutate()
                    .request(request.mutate().path("/index.html").build())
                    .build());
        }
        return chain.filter(exchange);
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