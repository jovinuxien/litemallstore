package org.linlinjava.litemall.gatewayapi.machine;

import org.linlinjava.litemall.gatewayapi.security.IdentityForwardingFilter;
import org.linlinjava.litemall.gatewayapi.security.PublicPaths;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Relays a service machine token on internal (load-balanced) routes.
 *
 * <p>For every eligible {@code lb://} route the customer edge sets
 * {@code Authorization: Bearer <machine-token>} on the proxied request. The customer
 * identity is carried separately as {@code X-User-*} by
 * {@code IdentityForwardingFilter}; downstream services (litemall-svcsecurity) trust
 * those headers only behind a valid machine token. Non-{@code lb} routes (today only
 * the {@code forward:} frontend route) are left untouched.
 *
 * <p><b>Eligibility is a security control, not an optimisation.</b> The token is
 * attached only to requests that are on the {@link PublicPaths public list} or that
 * carry a verified customer identity. Relaying unconditionally is what made the
 * missing edge gate exploitable: the machine token satisfies downstream
 * {@code authenticated()} checks, so an anonymous caller was promoted to a trusted
 * one on arrival. {@code SecurityConfig} now rejects those requests before routing,
 * making this a second, independent barrier — deliberately redundant, because either
 * one failing alone should not re-open the hole.
 */
@Component
public class MachineTokenRelayFilter implements GlobalFilter, Ordered {

    /** After route resolution, before the load-balancer/netty routing filters. */
    private static final int ORDER = 10100;

    private final ReactiveOAuth2AuthorizedClientManager clientManager;
    private final ServerWebExchangeMatcher publicPaths = PublicPaths.matcher();

    public MachineTokenRelayFilter(ReactiveOAuth2AuthorizedClientManager machineAuthorizedClientManager) {
        this.clientManager = machineAuthorizedClientManager;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || route.getUri() == null || !"lb".equals(route.getUri().getScheme())) {
            return chain.filter(exchange);
        }
        return eligible(exchange)
                .flatMap(ok -> Boolean.TRUE.equals(ok) ? relay(exchange, chain) : chain.filter(exchange));
    }

    /**
     * Public path, or a request carrying an identity {@code IdentityForwardingFilter}
     * verified. The header is trustworthy here precisely because that filter strips any
     * client-supplied {@code X-User-*} before re-injecting its own.
     */
    private Mono<Boolean> eligible(ServerWebExchange exchange) {
        if (exchange.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID) != null) {
            return Mono.just(true);
        }
        return publicPaths.matches(exchange).map(MatchResult::isMatch);
    }

    private Mono<Void> relay(ServerWebExchange exchange, GatewayFilterChain chain) {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId("authserver")
                .principal("gateway-api-machine")
                .build();
        return clientManager.authorize(request)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> exchange.mutate()
                        .request(r -> r.headers(h ->
                                h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue())))
                        .build())
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }
}
