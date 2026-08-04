package org.linlinjava.litemall.gatewayapi.security;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral Phase 0: the edge owns visitor identity, strictly behind consent.
 * Same conventions as {@link IdentityForwardingFilterTest} (no {@code @Nested};
 * runs only under {@code -Dmaven.test.skip=false} — check "Tests run:").
 */
class VisitorIdentityFilterTest {

    private static final String VID = "3f2b8a10-1111-4c39-9b1a-aaaaaaaaaaaa";
    private static final String SID = "3f2b8a10-2222-4c39-9b1a-bbbbbbbbbbbb";

    private VisitorIdentityFilter filter;
    private MockServerWebExchange exchange;
    private String forwardedVisitor;
    private String forwardedSession;

    @BeforeEach
    void setUp() {
        filter = new VisitorIdentityFilter(false);
        forwardedVisitor = null;
        forwardedSession = null;
    }

    private void run(MockServerHttpRequest request) {
        exchange = MockServerWebExchange.from(request);
        Mono<Void> result = filter.filter(exchange, ex -> {
            forwardedVisitor = ex.getRequest().getHeaders().getFirst(VisitorIdentityFilter.HDR_VISITOR_ID);
            forwardedSession = ex.getRequest().getHeaders().getFirst(VisitorIdentityFilter.HDR_SESSION_ID);
            return Mono.empty();
        });
        result.block();
    }

    private List<ResponseCookie> setCookies(String name) {
        return exchange.getResponse().getCookies().getOrDefault(name, List.of());
    }

    @Test
    @DisplayName("no consent cookie: no identity headers, no Set-Cookie, any region")
    void noConsentMeansNothing() {
        run(MockServerHttpRequest.get("/srv/goods/list").build());
        assertThat(forwardedVisitor).isNull();
        assertThat(forwardedSession).isNull();
        assertThat(exchange.getResponse().getCookies()).isEmpty();
    }

    @Test
    @DisplayName("granted consent mints both cookies and forwards them as headers")
    void grantedMintsAndForwards() {
        run(MockServerHttpRequest.post("/srv/track/collect")
                .cookie(new HttpCookie(VisitorIdentityFilter.COOKIE_CONSENT, "granted"))
                .build());
        assertThat(forwardedVisitor).isNotBlank();
        assertThat(forwardedSession).isNotBlank();
        ResponseCookie vid = setCookies(VisitorIdentityFilter.COOKIE_VISITOR).get(0);
        ResponseCookie sid = setCookies(VisitorIdentityFilter.COOKIE_SESSION).get(0);
        assertThat(vid.getValue()).isEqualTo(forwardedVisitor);
        assertThat(sid.getValue()).isEqualTo(forwardedSession);
        assertThat(vid.isHttpOnly()).isTrue();
        assertThat(vid.getMaxAge()).isEqualTo(VisitorIdentityFilter.VISITOR_TTL);
        assertThat(sid.getMaxAge()).isEqualTo(VisitorIdentityFilter.SESSION_TTL);
        assertThat(vid.getSameSite()).isEqualTo("Lax");
    }

    @Test
    @DisplayName("existing identity is reused; only the session cookie is re-sent (rolling)")
    void existingIdentityIsReusedRolling() {
        run(MockServerHttpRequest.get("/product/42")
                .cookie(new HttpCookie(VisitorIdentityFilter.COOKIE_CONSENT, "granted"),
                        new HttpCookie(VisitorIdentityFilter.COOKIE_VISITOR, VID),
                        new HttpCookie(VisitorIdentityFilter.COOKIE_SESSION, SID))
                .build());
        assertThat(forwardedVisitor).isEqualTo(VID);
        assertThat(forwardedSession).isEqualTo(SID);
        assertThat(setCookies(VisitorIdentityFilter.COOKIE_VISITOR)).isEmpty();
        assertThat(setCookies(VisitorIdentityFilter.COOKIE_SESSION)).hasSize(1);
    }

    @Test
    @DisplayName("a tampered (non-UUID) identity cookie is re-minted, never forwarded")
    void tamperedCookieIsReminted() {
        run(MockServerHttpRequest.get("/")
                .cookie(new HttpCookie(VisitorIdentityFilter.COOKIE_CONSENT, "granted"),
                        new HttpCookie(VisitorIdentityFilter.COOKIE_VISITOR, "'; drop table--"))
                .build());
        assertThat(forwardedVisitor).isNotEqualTo("'; drop table--");
        assertThat(setCookies(VisitorIdentityFilter.COOKIE_VISITOR)).hasSize(1);
    }

    @Test
    @DisplayName("inbound identity headers are always stripped — presence downstream is edge-attested")
    void inboundHeadersAlwaysStripped() {
        run(MockServerHttpRequest.post("/srv/track/collect")
                .header(VisitorIdentityFilter.HDR_VISITOR_ID, "spoofed")
                .header(VisitorIdentityFilter.HDR_SESSION_ID, "spoofed")
                .build());
        // No consent: the spoofed headers must be gone, not forwarded.
        assertThat(forwardedVisitor).isNull();
        assertThat(forwardedSession).isNull();
    }

    @Test
    @DisplayName("denied consent expires any existing identity cookies")
    void deniedExpiresIdentity() {
        run(MockServerHttpRequest.get("/cookies")
                .cookie(new HttpCookie(VisitorIdentityFilter.COOKIE_CONSENT, "denied"),
                        new HttpCookie(VisitorIdentityFilter.COOKIE_VISITOR, VID),
                        new HttpCookie(VisitorIdentityFilter.COOKIE_SESSION, SID))
                .build());
        assertThat(forwardedVisitor).isNull();
        assertThat(setCookies(VisitorIdentityFilter.COOKIE_VISITOR).get(0).getMaxAge().isZero()).isTrue();
        assertThat(setCookies(VisitorIdentityFilter.COOKIE_SESSION).get(0).getMaxAge().isZero()).isTrue();
    }

    @Test
    @DisplayName("denied consent with no identity cookies sets nothing")
    void deniedWithoutIdentityIsSilent() {
        run(MockServerHttpRequest.get("/")
                .cookie(new HttpCookie(VisitorIdentityFilter.COOKIE_CONSENT, "denied"))
                .build());
        assertThat(exchange.getResponse().getCookies()).isEmpty();
    }
}
