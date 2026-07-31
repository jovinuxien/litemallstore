package org.linlinjava.litemall.gatewayapi.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

/**
 * The customer edge's public surface, enumerated once.
 *
 * <p>Single source of truth, shared by {@link SecurityConfig} (which decides who may
 * pass) and {@code MachineTokenRelayFilter} (which decides who gets a downstream
 * machine token) via {@link #matcher()}. Those two must agree: a path the relay
 * treats as public but the policy rejects is merely broken, whereas a path the
 * policy rejects but the relay would have tokenised is the Wave-7 vulnerability
 * itself. One list, one matcher — they cannot drift.
 *
 * <p><b>Everything not listed here requires an authenticated customer.</b> This
 * matters more than it looks: the route table ends in an open {@code /srv/**}
 * catch-all to goods-management, so route ids cannot tell us which paths are public
 * — an unlisted path would otherwise proxy anonymously. Deny-by-default means a new
 * backend endpoint is unreachable until someone states it is public, rather than
 * silently exposed the day it ships.
 */
public final class PublicPaths {

    private PublicPaths() {
    }

    /**
     * Edge-local auth endpoints that must work logged-out. {@code /auth/**} is NOT
     * public wholesale: {@code /auth/me}, {@code /auth/profile} and {@code /auth/reset}
     * read {@code X-User-Id} and are authenticated below.
     *
     * <p>{@code /auth/reset/request|confirm} are public by necessity — a forgotten
     * password cannot present a token. Their anti-enumeration semantics (errno 701 when
     * disabled, otherwise uniform answers regardless of whether the account exists) are
     * what make that safe and must not regress.
     */
    static final String[] AUTH_ANY_METHOD = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/auth/reset/request",
            "/auth/reset/confirm",
            "/auth/site-config",
            // Wave 16: guest provisioning + Google Sign-In are login ENTRY
            // points — public by nature, exactly like /auth/login.
            // /auth/guest/claim is deliberately NOT here: claiming requires the
            // guest's own authenticated session (X-User-Id), like /auth/profile.
            "/auth/guest",
            "/auth/google",
    };

    /**
     * Read-only catalog, search and content — matched for GET only.
     *
     * <p>The GET qualifier is load-bearing, not tidiness: {@code /srv/comment/**} hosts
     * a public read ({@code list}, {@code count}) alongside an authenticated write
     * ({@code POST /srv/comment/post}). Were these matched for any method, that write
     * would ride in on the read's prefix.
     */
    static final String[] CATALOG_GET = {
            "/srv/catalog/**",
            "/srv/goods/**",
            "/srv/search/**",
            "/srv/suggest",
            "/srv/brand/**",
            "/srv/topic/**",
            "/srv/article/**",
            "/srv/page/**",
            "/srv/region/**",
            "/srv/store/list",
            "/srv/store/detail",
            "/srv/comment/list",
            "/srv/comment/count",
            "/srv/coupon/list",
            "/srv/groupon/list",
            "/srv/promotion/seckill/active",
            "/srv/promotion/combination",
    };

    /**
     * Stripe delivers webhooks with no credential this edge could check, so the path is
     * public here and signature-gated in litemall-order ({@code Webhook.constructEvent},
     * idempotent on {@code event.id}). It carries no identity by design — the handler
     * resolves the order from the Stripe payload.
     *
     * <p>The exact path comes from order's committed contract
     * (litemall-order/docs/handoff-stripe-checkout.md §7) and sits UNDER
     * {@code /srv/order/**}, which is otherwise authenticated — so this entry is what
     * stops every webhook 401ing. It needs no new route: the existing customer-order
     * route already sends {@code /srv/order/**} to the order service.
     */
    static final String STRIPE_WEBHOOK_POST = "/srv/order/webhook/stripe/**";

    /**
     * The API surface. Deny-by-default applies WITHIN these prefixes; a GET anywhere
     * else is the SPA shell — see {@link #spaShell()}.
     */
    private static final String[] API_PREFIXES = {
            "/srv/**",
            "/auth/**",
            "/actuator/**",
    };

    /**
     * Any GET that is not an API call: the SPA shell, its hashed bundles, and every
     * client-side route (`/cookies`, `/product/123`, …).
     *
     * <p>Public because it is static content — index.html and JS reveal nothing, and
     * every byte of data behind them arrives via {@code /srv/**}, which is gated. The
     * alternative, enumerating client-side routes here, would 401 a route the day
     * someone adds it to App.tsx and forgets this file — a booby trap, and one that
     * would make the legal pages (Task D) unlinkable.
     *
     * <p>Deny-by-default is unaffected where it earns its keep: an unlisted
     * {@code /srv/**} path still fails closed, because it matches API_PREFIXES.
     */
    static ServerWebExchangeMatcher spaShell() {
        List<ServerWebExchangeMatcher> api = new ArrayList<>();
        for (String p : API_PREFIXES) {
            api.add(new PathPatternParserServerWebExchangeMatcher(p));
        }
        return new AndServerWebExchangeMatcher(
                new PathPatternParserServerWebExchangeMatcher("/**", HttpMethod.GET),
                new NegatedServerWebExchangeMatcher(new OrServerWebExchangeMatcher(api)));
    }

    /**
     * Liveness only. {@code /actuator/gateway} is deliberately absent: it dumps the
     * full route table and was reachable anonymously before this change.
     */
    static final String[] ACTUATOR_GET = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
    };

    /**
     * The public surface as one matcher, for callers outside Spring Security's DSL.
     */
    public static ServerWebExchangeMatcher matcher() {
        List<ServerWebExchangeMatcher> matchers = new ArrayList<>();
        for (String p : AUTH_ANY_METHOD) {
            matchers.add(new PathPatternParserServerWebExchangeMatcher(p));
        }
        for (String p : CATALOG_GET) {
            matchers.add(new PathPatternParserServerWebExchangeMatcher(p, HttpMethod.GET));
        }
        for (String p : ACTUATOR_GET) {
            matchers.add(new PathPatternParserServerWebExchangeMatcher(p, HttpMethod.GET));
        }
        matchers.add(new PathPatternParserServerWebExchangeMatcher(STRIPE_WEBHOOK_POST, HttpMethod.POST));
        matchers.add(spaShell());
        return new OrServerWebExchangeMatcher(matchers);
    }
}
