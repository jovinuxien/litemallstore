package org.linlinjava.litemall.gatewayapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive security for the customer edge.
 *
 * <p>This is a BFF gateway, not a resource server: there is no Keycloak, no
 * OAuth2 login, no TokenRelay. Stateless, CSRF off (token-based, no cookies),
 * no form/basic login. Customer-token validation and trusted-identity
 * forwarding are done by {@link IdentityForwardingFilter}; per-route
 * authorization is layered in alongside the Phase 4 service machine token.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }
}