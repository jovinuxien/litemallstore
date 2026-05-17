package org.linlinjava.litemall.gatewayapi.machine;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Relays a service machine token on internal (load-balanced) routes.
 *
 * <p>For every {@code lb://} route the customer edge sets {@code Authorization:
 * Bearer <machine-token>} on the proxied request. The customer identity is
 * carried separately as {@code X-User-*} by {@code IdentityForwardingFilter};
 * downstream services (litemall-svcsecurity) trust those headers only behind a
 * valid machine token. Non-{@code lb} routes (e.g. the direct-URI wx-api
 * route) are left untouched.
 */
@Component
public class MachineTokenRelayFilter implements GlobalFilter, Ordered {

    /** After route resolution, before the load-balancer/netty routing filters. */
    private static final int ORDER = 10100;

    private final ReactiveOAuth2AuthorizedClientManager clientManager;

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
