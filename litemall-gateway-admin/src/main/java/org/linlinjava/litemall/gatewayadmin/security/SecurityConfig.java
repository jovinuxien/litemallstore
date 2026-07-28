package org.linlinjava.litemall.gatewayadmin.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.AuthoritiesConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Reactive security for the admin edge.
 *
 * <p>Phase 3c: the admin self-JWT now exists, so the admin edge enforces
 * authorization. A stateless {@link AuthenticationWebFilter} verifies the
 * Bearer admin JWT via {@link AdminJwtAuthenticationManager} and grants
 * {@code ROLE_ADMIN}; nothing is session-persisted
 * ({@link NoOpServerSecurityContextRepository}). No Keycloak/OIDC, no
 * TokenRelay. Trusted-identity header forwarding is done separately by
 * {@link IdentityForwardingFilter}. Hardening headers are preserved from
 * Phase 3b. The Phase 4 service machine token is layered on top later.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // img-src allows any https origin: product imagery is hotlinked from CJ's
    // CDN (cf./oss-cf.cjdropshipping.com, legacy aliyuncs hosts) — 'self'-only
    // blank-boxed every catalog image in the panel.
    public static final String CSP =
        "default-src 'self'; frame-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            JwtService adminJwtService) {
        AuthenticationWebFilter jwtFilter =
                new AuthenticationWebFilter(new AdminJwtAuthenticationManager(adminJwtService));
        jwtFilter.setServerAuthenticationConverter(bearerConverter());
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"))
                )
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(ex -> ex
                        // Public: auth endpoints, SPA shell/assets, health,
                        // open service paths (mirrors the former OIDC config).
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/", "/*.*", "/index.html", "/favicon.ico",
                                "/actuator/health/**").permitAll()
                        .pathMatchers("/srv/authenticate/**", "/srv/catalog/**",
                                "/srv/cjAuth/**").permitAll()
                        // Admin-only surfaces.
                        .pathMatchers("/srv/private/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        // Affiliate portal surface (Wave 5). AFFILIATE only —
                        // ADMIN is deliberately NOT allowed here (symmetric
                        // privilege separation: the order service self-scopes
                        // everything to the forwarded X-User-Id, and an admin's
                        // id would alias a litemall_user id).
                        .pathMatchers("/srv/private/affiliate/**").hasAuthority(AuthoritiesConstants.AFFILIATE)
                        // Order-module admin surface (e.g. order statistics for the
                        // admin dashboard). Gated to ADMIN so the validated identity
                        // is relayed downstream; the order service owns the logic.
                        .pathMatchers("/srv/order/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        .pathMatchers("/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        .pathMatchers("/srv/private/**").authenticated()
                        // Wallet ops (balance/debit/recharge/extract) are
                        // identity-sensitive: require a valid JWT so
                        // IdentityForwardingFilter injects X-User-* downstream.
                        .pathMatchers("/srv/wallet/**").authenticated()
                        // SPA client-side routes / remaining static assets.
                        .anyExchange().permitAll())
                .build();
    }

    /** Extracts a {@code Bearer} token into an unauthenticated token for the manager. */
    private static ServerAuthenticationConverter bearerConverter() {
        return (ServerWebExchange exchange) -> {
            String h = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = h.substring(7).trim();
                Authentication unauth = new UsernamePasswordAuthenticationToken(token, token);
                return Mono.just(unauth);
            }
            return Mono.empty();
        };
    }
}
