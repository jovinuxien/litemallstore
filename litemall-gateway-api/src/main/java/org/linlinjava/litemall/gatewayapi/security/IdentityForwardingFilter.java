package org.linlinjava.litemall.gatewayapi.security;

import java.util.List;
import java.util.Optional;

import org.linlinjava.litemall.db.auth.JwtService;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.auth0.jwt.interfaces.DecodedJWT;

import reactor.core.publisher.Mono;

/**
 * Validates the customer self-JWT at the edge and forwards a trusted identity
 * downstream.
 *
 * <p>The customer token is verified here and NOT relayed. Any inbound
 * {@code X-User-*} headers from the client are stripped first (anti-spoofing);
 * only if a Bearer token validates are {@code X-User-Id}/{@code X-User-Type}
 * re-injected for downstream services to trust. Downstream services accept
 * these headers only alongside a valid service machine token (added in
 * Phase 4) — the gateway is not yet a sufficient trust boundary on its own.
 *
 * <p>This filter establishes identity; {@link SecurityConfig} decides which paths
 * require it. The two are linked by the {@link Authentication} published into the
 * reactive security context here: nothing else in this module populates it (there
 * is no resource-server / {@code ReactiveJwtDecoder} — the token is decoded here,
 * by hand, with auth0 java-jwt), so without this the context would always be empty
 * and {@code .authenticated()} would reject every request.
 *
 * <p>Ordering matters and is load-bearing: this runs at
 * {@code HIGHEST_PRECEDENCE + 100}, far ahead of Spring Security's own chain
 * ({@code -100}), so the context is populated before authorization is evaluated.
 */
@Component
public class IdentityForwardingFilter implements WebFilter, Ordered {

    public static final String HDR_USER_ID = "X-User-Id";
    public static final String HDR_USER_TYPE = "X-User-Type";

    /** The only {@code typ} this edge issues (AuthController#loginPayload, #refresh). */
    static final String TYPE_CUSTOMER = "customer";
    static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";

    private final JwtService jwt;

    public IdentityForwardingFilter(JwtService customerJwtService) {
        this.jwt = customerJwtService;
    }

    @Override
    public int getOrder() {
        // Before Spring Cloud Gateway routing so mutated headers are forwarded.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        // Always drop client-supplied identity headers — never trust them.
        builder.headers(h -> {
            h.remove(HDR_USER_ID);
            h.remove(HDR_USER_TYPE);
        });

        Optional<DecodedJWT> decoded = bearer(exchange).flatMap(jwt::tryVerify);
        decoded.ifPresent(d -> builder.headers(h -> {
            h.set(HDR_USER_ID, d.getSubject());
            h.set(HDR_USER_TYPE, type(d));
        }));

        ServerWebExchange mutated = exchange.mutate().request(builder.build()).build();
        Mono<Void> downstream = chain.filter(mutated);

        // An unverified token is not an error here: it simply yields no identity, and
        // SecurityConfig turns that into a 401 on any non-public path. Public paths
        // stay reachable, which is what keeps anonymous browse/search working.
        return decoded
                .map(IdentityForwardingFilter::authenticationFor)
                .map(auth -> downstream.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .orElse(downstream);
    }

    private static String type(DecodedJWT token) {
        String type = token.getClaim("typ").asString();
        return type == null ? TYPE_CUSTOMER : type;
    }

    /**
     * The customer edge mints exactly one kind of token ({@code typ=customer}); the
     * admin and machine realms are separate issuers and never validate here. The
     * authority is granted on that basis, so an unexpected {@code typ} is authenticated
     * but deliberately unauthorized rather than treated as a customer.
     */
    private static Authentication authenticationFor(DecodedJWT token) {
        List<SimpleGrantedAuthority> authorities = TYPE_CUSTOMER.equals(type(token))
                ? List.of(new SimpleGrantedAuthority(ROLE_CUSTOMER))
                : List.of();
        return new UsernamePasswordAuthenticationToken(token.getSubject(), token, authorities);
    }

    private Optional<String> bearer(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.of(auth.substring(7).trim());
        }
        return Optional.empty();
    }
}