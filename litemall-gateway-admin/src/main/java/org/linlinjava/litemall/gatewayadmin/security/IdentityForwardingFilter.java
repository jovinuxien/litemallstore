package org.linlinjava.litemall.gatewayadmin.security;

import java.util.List;
import java.util.Optional;

import org.linlinjava.litemall.db.auth.JwtService;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.auth0.jwt.interfaces.DecodedJWT;

import reactor.core.publisher.Mono;

/**
 * Validates the admin self-JWT at the edge and forwards a trusted identity
 * downstream.
 *
 * <p>The admin token is verified here and NOT relayed. Any inbound
 * {@code X-User-*} headers from the client are stripped first (anti-spoofing);
 * only if a Bearer token validates are {@code X-User-Id}/{@code X-User-Type}/
 * {@code X-User-Roles} re-injected for downstream services to trust.
 * Downstream services accept these headers only alongside a valid service
 * machine token (added in Phase 4) — the gateway is not yet a sufficient
 * trust boundary on its own.
 *
 * <p>This runs before Spring Cloud Gateway routing so mutated headers are
 * forwarded; authorization is enforced separately by {@link SecurityConfig}.
 */
@Component
public class IdentityForwardingFilter implements WebFilter, Ordered {

    public static final String HDR_USER_ID = "X-User-Id";
    public static final String HDR_USER_TYPE = "X-User-Type";
    public static final String HDR_USER_ROLES = "X-User-Roles";

    private final JwtService jwt;

    public IdentityForwardingFilter(JwtService adminJwtService) {
        this.jwt = adminJwtService;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        // Always drop client-supplied identity headers — never trust them.
        builder.headers(h -> {
            h.remove(HDR_USER_ID);
            h.remove(HDR_USER_TYPE);
            h.remove(HDR_USER_ROLES);
        });

        Optional<DecodedJWT> decoded = bearer(exchange).flatMap(jwt::tryVerify);
        decoded.ifPresent(d -> builder.headers(h -> {
            h.set(HDR_USER_ID, d.getSubject());
            String type = d.getClaim("typ").asString();
            h.set(HDR_USER_TYPE, type == null ? "admin" : type);
            List<String> roles = d.getClaim("roles").asList(String.class);
            if (roles != null && !roles.isEmpty()) {
                h.set(HDR_USER_ROLES, String.join(",", roles));
            }
        }));

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private Optional<String> bearer(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.of(auth.substring(7).trim());
        }
        return Optional.empty();
    }
}
