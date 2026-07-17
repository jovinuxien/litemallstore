package org.linlinjava.litemall.gatewayapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.auth.JwtProperties;
import org.linlinjava.litemall.db.auth.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Identity establishment at the edge (Wave-7 Task A).
 *
 * <p>Covers the half {@link EdgeAuthorizationPolicyTest} deliberately stubs out: that a
 * verified token — and nothing else — produces the {@code Authentication} the policy
 * reads, and the {@code X-User-*} headers downstream services trust.
 *
 * <p>Uses a real {@link JwtService} with an ephemeral key pair (empty PEMs) rather than
 * a mock, so the sign/verify round trip is genuine.
 */
class IdentityForwardingFilterTest {

    private JwtService jwt;
    private IdentityForwardingFilter filter;

    /** What the next filter saw — i.e. exactly what would have been proxied. */
    private ServerWebExchange forwarded;

    /** The authentication visible to Spring Security, or null if the request stayed anonymous. */
    private Authentication captured;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setIssuer("litemall-customer");
        props.setAudience("litemall-customer-api");
        props.setAccessTtlSeconds(7200);
        jwt = JwtService.create(props);
        filter = new IdentityForwardingFilter(jwt);
        forwarded = null;
        captured = null;
    }

    private void run(MockServerHttpRequest request) {
        Mono<Void> result = filter.filter(MockServerWebExchange.from(request), ex -> {
            forwarded = ex;
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .doOnNext(a -> captured = a)
                    .then();
        });
        StepVerifier.create(result).verifyComplete();
    }

    private String customerToken(int uid) {
        return jwt.issue(String.valueOf(uid), Map.of("uid", uid, "typ", "customer"));
    }

    @Test
    @DisplayName("a valid token yields a ROLE_CUSTOMER authentication and trusted headers")
    void validTokenEstablishesIdentity() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/srv/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken(7))
                .build();

        run(request);

        assertThat(captured).isNotNull();
        assertThat(captured.getName()).isEqualTo("7");
        assertThat(captured.getAuthorities()).extracting(Object::toString)
                .containsExactly(IdentityForwardingFilter.ROLE_CUSTOMER);
        assertThat(forwarded.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID))
                .isEqualTo("7");
        assertThat(forwarded.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_TYPE))
                .isEqualTo("customer");
    }

    /**
     * The anti-spoofing property the whole trust chain rests on: downstream services accept
     * {@code X-User-Id} as identity, so a client-supplied one must never survive the edge.
     */
    @Test
    @DisplayName("client-supplied identity headers are stripped, not honoured")
    void spoofedIdentityHeadersAreStripped() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/srv/cart/items")
                .header(IdentityForwardingFilter.HDR_USER_ID, "2")
                .header(IdentityForwardingFilter.HDR_USER_TYPE, "customer")
                .build();

        run(request);

        assertThat(captured).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID)).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_TYPE)).isNull();
    }

    @Test
    @DisplayName("a spoofed header cannot override a real token")
    void spoofedHeaderLosesToVerifiedToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/srv/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken(7))
                .header(IdentityForwardingFilter.HDR_USER_ID, "2")
                .build();

        run(request);

        assertThat(forwarded.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID))
                .isEqualTo("7");
    }

    /**
     * Garbage in the Authorization header must not throw — it simply yields no identity,
     * which SecurityConfig turns into a 401 on protected paths while leaving public ones
     * reachable.
     */
    @Test
    @DisplayName("an unverifiable token yields no identity rather than an error")
    void invalidTokenIsAnonymous() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/srv/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .build();

        run(request);

        assertThat(captured).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID)).isNull();
    }

    @Test
    @DisplayName("a token from another realm authenticates but is not a customer")
    void foreignTokenTypeGetsNoCustomerRole() {
        String adminish = jwt.issue("9", Map.of("uid", 9, "typ", "admin"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/srv/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminish)
                .build();

        run(request);

        assertThat(captured.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("no Authorization header is simply anonymous")
    void noTokenIsAnonymous() {
        run(MockServerHttpRequest.get("/srv/goods/list").build());
        assertThat(captured).isNull();
    }
}
