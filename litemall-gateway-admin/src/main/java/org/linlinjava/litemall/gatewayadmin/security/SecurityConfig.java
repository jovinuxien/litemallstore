package org.linlinjava.litemall.gatewayadmin.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;

/**
 * Reactive security for the admin edge.
 *
 * <p>Phase 3b: Keycloak/OIDC has been removed. This is a BFF gateway, not a
 * resource server — no oauth2Login, no TokenRelay, no OIDC logout. Stateless,
 * CSRF off (token-based, no cookies), no form/basic login. The hardening
 * headers (CSP, frame-options, referrer/permissions policy) are preserved
 * from the former {@code GatewaySecurityConfig}.
 *
 * <p>Authorization is intentionally {@code permitAll} here: the admin
 * self-JWT that can satisfy an {@code ADMIN} authority does not exist until
 * Phase 3c. Edge JWT validation, trusted-identity forwarding
 * ({@code IdentityForwardingFilter}) and the {@code ADMIN} gate on
 * {@code /srv/private/admin/**} are added in Phase 3c alongside the JWT, then
 * layered with the service machine token in Phase 4.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String CSP =
        "default-src 'self'; frame-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"))
                )
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }
}