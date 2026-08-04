package org.linlinjava.litemall.gatewayapi.security;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Edge-owned visitor identity for the first-party behavioral log (behavioral
 * targeting Phase 0 — contract: doc/behavioral-events.md).
 *
 * <p>STRICT prior consent (user decision 2026-08-04): identity exists only
 * while the SPA-written {@code lm_consent} cookie says {@code granted}. Under a
 * grant this filter mints {@code lm_vid} (13-month) / {@code lm_sid} (30-minute
 * rolling — re-sent on every request, so inactivity ends the session) as
 * HttpOnly cookies and forwards them downstream as
 * {@code X-Visitor-Id}/{@code X-Session-Id}. On {@code denied} it expires any
 * identity cookies. With no consent cookie at all it does nothing — no cookies,
 * no headers, any region.
 *
 * <p>Inbound {@code X-Visitor-Id}/{@code X-Session-Id} are ALWAYS stripped
 * (same anti-spoofing stance as {@link IdentityForwardingFilter} takes for
 * {@code X-User-*}): the client can never assert an identity, so the
 * goods-management ingest may treat the header's presence as edge-attested
 * consent proof. Cookie values are validated as UUIDs — a tampered cookie is
 * simply re-minted, never forwarded.
 *
 * <p>Runs right after {@link IdentityForwardingFilter} (far before routing) so
 * the mutated headers reach the downstream request.
 */
@Component
public class VisitorIdentityFilter implements WebFilter, Ordered {

    public static final String HDR_VISITOR_ID = "X-Visitor-Id";
    public static final String HDR_SESSION_ID = "X-Session-Id";

    static final String COOKIE_CONSENT = "lm_consent";
    static final String COOKIE_VISITOR = "lm_vid";
    static final String COOKIE_SESSION = "lm_sid";
    static final String CONSENT_GRANTED = "granted";
    static final String CONSENT_DENIED = "denied";

    /** 13 months — the visitor id's whole lifetime, aligned with the consent cookie. */
    static final Duration VISITOR_TTL = Duration.ofSeconds(34128000L);
    /** 30-minute inactivity window, refreshed on every request. */
    static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private static final Pattern UUID_36 =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final boolean cookieSecure;

    public VisitorIdentityFilter(@Value("${litemall.tracking.cookie-secure:false}") boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    @Override
    public int getOrder() {
        // Just after IdentityForwardingFilter (+100): headers must be mutated
        // before Spring Security (-100) and long before gateway routing.
        return Ordered.HIGHEST_PRECEDENCE + 110;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        // Always drop client-supplied identity headers — never trust them.
        builder.headers(h -> {
            h.remove(HDR_VISITOR_ID);
            h.remove(HDR_SESSION_ID);
        });

        String consent = cookieValue(exchange, COOKIE_CONSENT);
        String visitor = validUuid(cookieValue(exchange, COOKIE_VISITOR));
        String session = validUuid(cookieValue(exchange, COOKIE_SESSION));

        if (CONSENT_GRANTED.equals(consent)) {
            String vid = visitor != null ? visitor : UUID.randomUUID().toString();
            String sid = session != null ? session : UUID.randomUUID().toString();
            if (visitor == null) {
                exchange.getResponse().addCookie(identityCookie(COOKIE_VISITOR, vid, VISITOR_TTL));
            }
            // Rolling expiry: every request restarts the 30-minute session clock.
            exchange.getResponse().addCookie(identityCookie(COOKIE_SESSION, sid, SESSION_TTL));
            builder.headers(h -> {
                h.set(HDR_VISITOR_ID, vid);
                h.set(HDR_SESSION_ID, sid);
            });
        } else if (CONSENT_DENIED.equals(consent) && (visitor != null || session != null)) {
            // Withdrawal: identity ends now. (Event rows already written stay —
            // erasure is a query-time/GDPR-request concern, not a cookie one.)
            exchange.getResponse().addCookie(identityCookie(COOKIE_VISITOR, "", Duration.ZERO));
            exchange.getResponse().addCookie(identityCookie(COOKIE_SESSION, "", Duration.ZERO));
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private ResponseCookie identityCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .build();
    }

    private static String cookieValue(ServerWebExchange exchange, String name) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie == null ? null : cookie.getValue();
    }

    private static String validUuid(String value) {
        return value != null && UUID_36.matcher(value).matches() ? value.toLowerCase() : null;
    }
}
