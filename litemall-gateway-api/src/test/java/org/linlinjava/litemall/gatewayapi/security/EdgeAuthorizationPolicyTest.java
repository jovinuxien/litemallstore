package org.linlinjava.litemall.gatewayapi.security;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;

/**
 * The edge authorization matrix (Wave-7 Task A).
 *
 * <p>This is the first test in this module, and it exists because the thing being
 * shipped is the ABSENCE of a vulnerability — which does not demo and does not stay
 * fixed on its own. {@link PublicPaths} is a hand-maintained list guarding a route
 * table that ends in an open {@code /srv/**} catch-all, so the failure mode is
 * silent: someone adds a backend endpoint, nobody adds it here, and it is either
 * anonymously exposed (if listed too broadly) or mysteriously 401s (if not).
 *
 * <p>The chain is assembled directly rather than through {@code @SpringBootTest} so the
 * policy can be asserted without booting Eureka, the config server, or the downstream
 * services — this stays a fast unit test of the rules themselves.
 *
 * <p>Two deliberate conventions, both learned the hard way in this repo:
 * <ul>
 * <li>No {@code @Nested} classes — surefire 3.0.0-M5 here discovers ZERO tests inside
 * them and still reports BUILD SUCCESS.
 * <li>The root pom sets {@code maven.test.skip=true}, so these run only under
 * {@code -Dmaven.test.skip=false}. A green build is not evidence they ran; check the
 * "Tests run:" count.
 * </ul>
 *
 * <p>Scope note: this asserts the POLICY. That {@link IdentityForwardingFilter} is what
 * populates the context the policy reads is covered by {@link IdentityForwardingFilterTest}.
 */
class EdgeAuthorizationPolicyTest {

    /** Stands in for "the request reached the proxy" — a 200 here means the policy let it through. */
    private static final WebHandler PASS_THROUGH = exchange -> {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return exchange.getResponse().setComplete();
    };

    private WebTestClient anonymous;
    private WebTestClient customer;

    @BeforeEach
    void setUp() {
        SecurityWebFilterChain chain = new SecurityConfig().springSecurityFilterChain(ServerHttpSecurity.http());
        anonymous = WebTestClient.bindToWebHandler(PASS_THROUGH)
                .webFilter(new WebFilterChainProxy(chain))
                .build();
        // Ahead of the security chain, exactly where IdentityForwardingFilter sits in
        // production, publishing the same authentication it would for a valid token.
        // (Spring Security's mockAuthentication mutator silently no-ops on a
        // bindToWebHandler client — it has no WebHttpHandlerBuilder to attach to.)
        customer = WebTestClient.bindToWebHandler(PASS_THROUGH)
                .webFilter(authenticatedAs(customerAuthentication()), new WebFilterChainProxy(chain))
                .build();
    }

    private static Authentication customerAuthentication() {
        return new UsernamePasswordAuthenticationToken("1", "token",
                List.of(new SimpleGrantedAuthority(IdentityForwardingFilter.ROLE_CUSTOMER)));
    }

    private static WebFilter authenticatedAs(Authentication auth) {
        return (exchange, chain) -> chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    // ---------------------------------------------------------------- public surface

    @Test
    @DisplayName("catalog, search and content stay reachable anonymously")
    void catalogSearchAndContentAreOpen() {
        for (String path : new String[] {
                "/srv/catalog/all", "/srv/goods/detail?id=1", "/srv/goods/list", "/srv/search?keyword=x",
                "/srv/search/suggest?q=x", "/srv/brand/list", "/srv/topic/list", "/srv/article/list",
                "/srv/region/list", "/srv/store/list", "/srv/comment/list?valueId=1",
                "/srv/comment/count?valueId=1", "/srv/coupon/list", "/srv/groupon/list",
                "/srv/promotion/seckill/active", "/srv/promotion/combination" }) {
            anonymous.get().uri(path).exchange().expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("logged-out auth endpoints stay open")
    void loggedOutAuthEndpointsAreOpen() {
        for (String path : PublicPaths.AUTH_ANY_METHOD) {
            anonymous.post().uri(path).exchange().expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("guest claim requires the guest's own session (Wave 16)")
    void guestClaimIsAuthenticatedOnly() {
        // /auth/guest and /auth/google are asserted public by the AUTH_ANY_METHOD
        // loop above; the claim endpoint must NOT ride along — an anonymous
        // claim would let anyone set a password on a shadow account.
        anonymous.post().uri("/auth/guest/claim").exchange().expectStatus().isUnauthorized();
        customer.post().uri("/auth/guest/claim").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("health is open but the route table is not")
    void healthIsOpenButTheRouteTableIsNot() {
        anonymous.get().uri("/actuator/health").exchange().expectStatus().isOk();
        // /actuator/gateway dumps every route, target and predicate. It was exposed AND
        // anonymous before Wave-7 (management.endpoints...include: health,info,gateway).
        anonymous.get().uri("/actuator/gateway").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Path per order's committed contract (handoff-stripe-checkout.md §7). It lives under
     * the otherwise-authenticated /srv/order/** prefix, so this is a genuine carve-out —
     * get it wrong and every webhook 401s while the order sits unpaid.
     */
    @Test
    @DisplayName("the Stripe webhook is anonymous, because Stripe cannot present a token")
    void stripeWebhookIsAnonymous() {
        anonymous.post().uri("/srv/order/webhook/stripe/order-paid").exchange().expectStatus().isOk();
        // ...but only that path, and only POST — it is signature-gated downstream, not a
        // general-purpose hole punched in /srv/order/**.
        anonymous.get().uri("/srv/order/webhook/stripe/order-paid").exchange().expectStatus().isUnauthorized();
        anonymous.post().uri("/srv/order/123/actions/pay").exchange().expectStatus().isUnauthorized();
        anonymous.post().uri("/srv/order/webhook/other").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("CORS preflight needs no credential")
    void corsPreflightNeedsNoCredential() {
        anonymous.options().uri("/srv/cart/items").exchange().expectStatus().isOk();
    }

    /**
     * The SPA shell is static content and must be reachable logged-out — including
     * client-side routes, which exist only in App.tsx and would otherwise 401 the day
     * someone adds one. The legal pages (Task D) live here.
     */
    @Test
    @DisplayName("the SPA shell and its client-side routes are public")
    void spaShellIsPublic() {
        for (String path : new String[] {
                "/", "/index.html", "/favicon.ico", "/app/main.abc123.js",
                "/cookies", "/privacy", "/terms", "/returns", "/login", "/product/42" }) {
            anonymous.get().uri(path).exchange().expectStatus().isOk();
        }
    }

    /**
     * The SPA-shell rule must not become a hole: it is GET-only and stops at the API
     * prefixes, so deny-by-default still governs everything that carries data.
     */
    @Test
    @DisplayName("the SPA-shell rule does not leak into the API surface")
    void spaShellDoesNotCoverTheApi() {
        anonymous.get().uri("/srv/cart/items").exchange().expectStatus().isUnauthorized();
        anonymous.get().uri("/auth/me").exchange().expectStatus().isUnauthorized();
        anonymous.get().uri("/actuator/gateway").exchange().expectStatus().isUnauthorized();
        // Non-GET outside the public list is not "the shell" either.
        anonymous.post().uri("/cookies").exchange().expectStatus().isUnauthorized();
    }

    // ------------------------------------------------------------- protected surface

    /**
     * Each of these was reachable anonymously before Wave-7: the edge permitted everything
     * and the relay then attached a machine token that satisfied the downstream
     * {@code authenticated()} check on the caller's behalf.
     */
    @Test
    @DisplayName("money and identity paths reject anonymous callers")
    void moneyAndIdentityPathsAre401() {
        for (String path : new String[] {
                "/srv/cart/items", "/srv/order/list", "/srv/address/list", "/srv/wallet/balance",
                "/auth/me", "/auth/profile" }) {
            anonymous.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
    }

    /** Not named in the Wave-7 plan; found during the route audit. Same hole, same class. */
    @Test
    @DisplayName("user-scoped engagement paths reject anonymous callers")
    void engagementPathsAre401() {
        for (String path : new String[] {
                "/srv/collect/list", "/srv/footprint/list", "/srv/coupon/mylist", "/srv/coupon/selectlist",
                "/srv/promotion/coupon/available" }) {
            anonymous.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
    }

    /**
     * The reason the catalog rules are GET-only: /srv/comment/** carries a public read and
     * an authenticated write on the same prefix.
     */
    @Test
    @DisplayName("comment read is public but posting is not")
    void commentReadIsPublicButPostIsNot() {
        anonymous.get().uri("/srv/comment/list?valueId=1").exchange().expectStatus().isOk();
        anonymous.post().uri("/srv/comment/post").exchange().expectStatus().isUnauthorized();
        customer.post().uri("/srv/comment/post").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("feedback and upload writes reject anonymous callers")
    void feedbackAndUploadWritesAre401() {
        anonymous.post().uri("/srv/feedback/submit").exchange().expectStatus().isUnauthorized();
        anonymous.post().uri("/srv/storage/upload").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Deny-by-default. An unlisted /srv path must NOT inherit the catch-all's anonymous
     * reach just because nobody has classified it yet.
     */
    @Test
    @DisplayName("unlisted /srv paths fail closed")
    void unknownSrvPathsFailClosed() {
        anonymous.get().uri("/srv/some/endpoint/shipped/next/wave").exchange().expectStatus().isUnauthorized();
        anonymous.post().uri("/srv/anything").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("an authenticated customer reaches the protected surface")
    void authenticatedCustomerReachesProtectedPaths() {
        for (String path : new String[] {
                "/srv/cart/items", "/srv/order/list", "/srv/wallet/balance", "/auth/me" }) {
            customer.get().uri(path).exchange().expectStatus().isOk();
        }
    }
}
