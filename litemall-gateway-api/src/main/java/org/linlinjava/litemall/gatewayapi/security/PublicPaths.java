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
            // Wave-21 group-buy reads: the PDP strip and the shareable
            // /groupon/:id landing must work logged-out. The parent entry above
            // is an exact PathPattern match (legacy list) — it does NOT cover
            // these children, and the miss sent every anonymous PDP visit to
            // /login via the SPA's 401 hook. GET /my stays authenticated (it
            // reads X-User-Id), which is why this is not a blanket /**.
            "/srv/promotion/combination/active",
            "/srv/promotion/combination/{combinationId:\\d+}",
            "/srv/promotion/combination/pink/{pinkId:\\d+}",
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
     * Behavioral targeting Phase 0 (doc/behavioral-events.md): the first-party
     * event ingest is anonymous by nature — pre-login intent is the point. The
     * second sanctioned public POST after the Stripe webhook (user-approved
     * exception, 2026-08-04). What keeps it safe: the goods-management handler
     * is insert-only behind an event-type whitelist and hard size caps, identity
     * is edge-owned ({@link org.linlinjava.litemall.gatewayapi.security.VisitorIdentityFilter}
     * strips inbound {@code X-Visitor-Id}/{@code X-Session-Id} and injects them
     * only under a granted consent cookie), and batches arriving without that
     * attested identity are dropped server-side.
     */
    static final String TRACK_POST = "/srv/track/**";

    /**
     * Bulk goods read, which is a POST only because its argument is a list of ids
     * in the body ({@code batchGoods(@RequestBody Set<Integer>)}).
     *
     * <p>Wave 27 found this the hard way: a DIY page whose {@code goods-list}
     * component uses {@code mode: byIds} — the mode the season/coupon curation
     * procedure tells admins to pick — resolves its products through this call,
     * so with the path authenticated the whole rail rendered EMPTY for every
     * logged-out shopper while looking perfectly healthy to a signed-in admin
     * previewing it. Verified against production: {@code GET /srv/goods/detail}
     * 200, {@code POST /srv/goods/batch} 401.
     *
     * <p>It exposes nothing new: the same aggregate is already public one id at a
     * time via {@code GET /srv/goods/**}, and goods-management's own
     * {@code public-paths} has always allowed {@code /srv/goods/**} for any
     * method — the GET qualifier here was the only gate. The entry is the exact
     * path, not a prefix, so no future {@code POST /srv/goods/*} write rides in
     * on it.
     *
     * <p>Known limit, raised for goods-management rather than worked around
     * here: the handler applies no cap to the id set, so this is an
     * amplification surface (the palette caps a component at 24 ids, but the
     * endpoint accepts any body). A server-side cap belongs in the handler.
     */
    static final String GOODS_BATCH_POST = "/srv/goods/batch";

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
        matchers.add(new PathPatternParserServerWebExchangeMatcher(TRACK_POST, HttpMethod.POST));
        matchers.add(new PathPatternParserServerWebExchangeMatcher(GOODS_BATCH_POST, HttpMethod.POST));
        matchers.add(spaShell());
        return new OrServerWebExchangeMatcher(matchers);
    }
}
