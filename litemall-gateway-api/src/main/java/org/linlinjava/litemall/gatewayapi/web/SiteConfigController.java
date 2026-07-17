package org.linlinjava.litemall.gatewayapi.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime SPA bootstrap config (Wave-6 Task A). The customer SPA fetches this
 * once at startup to decide whether to inject the Matomo tracker: both values
 * present ⇒ inject; either null (the committed default) ⇒ the bundle stays
 * byte-identical in behavior — no matomo script, no tracker requests.
 *
 * <p>Lives under {@code /auth} purely for plumbing: that prefix is served
 * locally by edge controllers (not routed downstream) and is already proxied
 * by the SPA dev-server, so dev and prod need no extra wiring. The payload is
 * public, non-sensitive config (the Matomo auth token is NOT exposed — that is
 * promotion's server-side Reporting-API credential; the browser tracker
 * endpoint needs none).
 *
 * <p>Wave-7 adds {@code stripePublishableKey} on the same terms, per order's
 * contract (litemall-order/docs/handoff-stripe-checkout.md §3). A publishable key
 * is designed to be public — but it is environment-specific, so there is no
 * committed fallback: absent ⇒ null ⇒ the SPA presents card payment as cleanly
 * unavailable rather than stubbing it. The SECRET key belongs to litemall-order
 * ({@code litemall.order.stripe.secret-key}, ENV only) and must never appear here.
 *
 * <p>Deliberately NOT {@code litemall.stripe.publishedKey}: litemall-core's
 * application-core.yml ships committed {@code pk_test_}/{@code sk_test_} values
 * under that legacy block. This edge does not load the core profile
 * ({@code active: default,db}), so there is no precedence trap today — but reusing
 * the key name would invite one, and would hand out a committed test key by
 * default. Retiring that block is flagged to platform.
 */
@RestController
@RequestMapping("/auth")
public class SiteConfigController {

    private final String matomoUrl;
    private final String matomoSiteId;
    private final int matomoGoodsDimension;
    private final String stripePublishableKey;

    public SiteConfigController(
            @Value("${litemall.tracking.matomo.base-url:}") String matomoUrl,
            @Value("${litemall.tracking.matomo.site-id:}") String matomoSiteId,
            @Value("${litemall.tracking.matomo.goods-dimension:1}") int matomoGoodsDimension,
            @Value("${litemall.stripe.publishable-key:}") String stripePublishableKey) {
        this.matomoUrl = blankToNull(matomoUrl);
        this.matomoSiteId = blankToNull(matomoSiteId);
        this.matomoGoodsDimension = matomoGoodsDimension;
        this.stripePublishableKey = blankToNull(stripePublishableKey);
    }

    @GetMapping("/site-config")
    public Map<String, Object> siteConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("matomoUrl", matomoUrl);
        data.put("matomoSiteId", matomoSiteId);
        data.put("matomoGoodsDimension", matomoGoodsDimension);
        data.put("stripePublishableKey", stripePublishableKey);
        return ApiResponse.ok(data);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
