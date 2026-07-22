package org.linlinjava.litemall.gatewayapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;

/**
 * Reactive security for the customer edge.
 *
 * <p>This is a BFF gateway, not a resource server: there is no Keycloak, no OAuth2
 * login, no TokenRelay. Stateless, CSRF off (token-based, no cookies), no
 * form/basic login.
 *
 * <p>Identity comes from {@link IdentityForwardingFilter}, which validates the
 * customer self-JWT and publishes an {@code Authentication} into the reactive
 * security context. That link is what makes the rules below work at all — nothing
 * else populates the context, so {@code .authenticated()} against a stock
 * configuration would reject every request rather than just anonymous ones.
 *
 * <p><b>Deny-by-default.</b> {@link PublicPaths} enumerates the public surface and
 * everything else needs a customer. The policy cannot be derived from the route
 * table: it ends in an open {@code /srv/**} catch-all, so an unlisted path is an
 * anonymously-proxied path. Pairing this with the relay gate in
 * {@code MachineTokenRelayFilter} closes both halves — the gate alone would not,
 * because the relay attaches a machine token that satisfies downstream
 * {@code authenticated()} checks on a caller's behalf.
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
                // A browser client expects a status, never a redirect or a WWW-Authenticate
                // challenge: the SPA maps 401 onto its own sign-in flow.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN)))
                .authorizeExchange(ex -> ex
                        // CORS preflight carries no credentials by design.
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .matchers(PublicPaths.matcher()).permitAll()
                        // Wallet credit/debit are NOT customer self-service: credit mints
                        // balance with no payment, debit is internal to the pay flow. Left
                        // open they let any signed-in customer fund themselves and check
                        // out for free. Denied outright at the customer edge — customers
                        // top up via /srv/wallet/recharge (payment-gated) only.
                        .pathMatchers(org.springframework.http.HttpMethod.POST,
                                "/srv/wallet/credit", "/srv/wallet/debit").denyAll()
                        // Everything else — /srv/cart, /srv/order, /srv/address, /srv/wallet,
                        // /srv/collect, /srv/footprint, /srv/feedback, POST /srv/comment/post,
                        // /srv/coupon/{mylist,receive,selectlist}, /srv/storage/upload,
                        // /auth/{me,profile,reset}, /actuator/gateway, and any unlisted
                        // /srv/** that the catch-all would otherwise proxy anonymously.
                        .anyExchange().hasRole("CUSTOMER"))
                .build();
    }
}
