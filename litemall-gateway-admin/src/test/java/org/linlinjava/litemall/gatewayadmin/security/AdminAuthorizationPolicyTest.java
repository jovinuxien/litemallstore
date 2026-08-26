package org.linlinjava.litemall.gatewayadmin.security;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.AuthoritiesConstants;
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
 * The admin edge's authorization matrix.
 *
 * <p>Written after {@code /admin/**} was found requiring ADMIN authority. Those are the React
 * router's CLIENT routes — the only thing served there is the SPA shell — but a browser
 * navigation carries no Authorization header, because the SPA attaches its JWT from JS to API
 * calls. So every bookmark, every F5 and every link opened in a new tab got a bare 401 with a
 * Basic-auth challenge, and the user saw a login prompt. Reported as "clicking a product link
 * asks me to log in again".
 *
 * <p>The invariant worth protecting is a PAIR, and asserting only half of it would be worse
 * than asserting neither: the client routes must be public, AND the data behind them must not
 * be. Both are checked here.
 *
 * <p>Chain assembled directly rather than via {@code @SpringBootTest}, so the rules are tested
 * without booting Eureka, the config server or any downstream service — the same approach as
 * the storefront's EdgeAuthorizationPolicyTest.
 *
 * <p>⚠ No {@code @Nested} classes: surefire here discovers ZERO tests inside them and still
 * reports BUILD SUCCESS. Read the "Tests run:" count.
 */
class AdminAuthorizationPolicyTest {

    /** A 200 means the policy let the request through to the handler. */
    private static final WebHandler PASS_THROUGH = exchange -> {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return exchange.getResponse().setComplete();
    };

    private WebTestClient anonymous;
    private WebTestClient admin;

    @BeforeEach
    void setUp() {
        // The JWT filter's manager is only invoked when an Authorization: Bearer header is
        // present, and nothing here sends one — the authenticated client publishes the
        // context directly, exactly as a verified token would.
        SecurityWebFilterChain chain =
                new SecurityConfig().springSecurityFilterChain(ServerHttpSecurity.http(), null);
        anonymous = WebTestClient.bindToWebHandler(PASS_THROUGH)
                .webFilter(new WebFilterChainProxy(chain))
                .build();
        admin = WebTestClient.bindToWebHandler(PASS_THROUGH)
                .webFilter(authenticatedAs(adminAuthentication()), new WebFilterChainProxy(chain))
                .build();
    }

    private static Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken("1", "token",
                List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)));
    }

    private static WebFilter authenticatedAs(Authentication auth) {
        return (exchange, chain) -> chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    // ------------------------------------------------- the regression: client routes

    @Test
    @DisplayName("admin client routes are reachable without a token, so deep links work")
    void adminClientRoutesArePublic() {
        for (String path : new String[] {
                "/admin",
                "/admin/dashboard",
                "/admin/goods/seo-titles",
                "/admin/goods/10000844/edit",   // the exact link the SEO page renders
                "/admin/mall/order/123",
        }) {
            anonymous.get().uri(path).exchange().expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("the SPA shell and its assets stay public")
    void shellAndAssetsArePublic() {
        anonymous.get().uri("/").exchange().expectStatus().isOk();
        anonymous.get().uri("/index.html").exchange().expectStatus().isOk();
        anonymous.get().uri("/favicon.ico").exchange().expectStatus().isOk();
        anonymous.get().uri("/actuator/health").exchange().expectStatus().isOk();
        anonymous.get().uri("/auth/login").exchange().expectStatus().isOk();
    }

    // ------------------------------------------------- the other half: the data

    @Test
    @DisplayName("admin DATA is still gated — this is what makes the routes safe to open")
    void adminDataStillRequiresAdmin() {
        for (String path : new String[] {
                "/srv/private/admin/seo/titles",
                "/srv/private/admin/order/list",
                "/srv/private/admin/insight/categories",
                "/srv/order/admin/statistics",
        }) {
            anonymous.get().uri(path).exchange().expectStatus().isUnauthorized();
        }
    }

    @Test
    @DisplayName("an authenticated admin reaches the admin data surfaces")
    void adminReachesAdminData() {
        admin.get().uri("/srv/private/admin/seo/titles").exchange().expectStatus().isOk();
        admin.get().uri("/srv/private/admin/order/list").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("the affiliate surface is not reachable by an admin (privilege separation)")
    void affiliateSurfaceIsNotAdminReachable() {
        admin.get().uri("/srv/private/affiliate/promoter").exchange().expectStatus().isForbidden();
        anonymous.get().uri("/srv/private/affiliate/promoter").exchange().expectStatus().isUnauthorized();
    }
}
