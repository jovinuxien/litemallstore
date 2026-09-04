package org.linlinjava.litemall.gatewayapi.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
 *
 * <p>Wave-9.1 adds the footer social-profile URLs on the same terms. Null ⇒ the
 * SPA renders no icon for that network — a page that does not exist yet must be
 * a hidden icon, not a dead link. Facebook has a committed default (the page is
 * live); the rest go live via ENV ({@code LITEMALL_SOCIAL_INSTAGRAM_URL} etc.)
 * plus a container recreate — no image rebuild, since the SPA reads these at
 * runtime from this endpoint.
 *
 * <p>Wave-15 adds {@code metaPixelId} on the same terms: a pixel id is public
 * by design, null ⇒ the SPA injects no pixel and makes no Meta request (and the
 * pixel is additionally consent-gated client-side either way). Goes live via
 * ENV ({@code LITEMALL_META_PIXEL_ID}) plus a container recreate.
 *
 * <p>i18n foundation adds {@code i18nLanguages}: the storefront languages the
 * operator has enabled, from {@code litemall.i18n.languages} (ENV
 * {@code LITEMALL_I18N_LANGUAGES}, comma-separated, default {@code en}). The SPA
 * renders its language switcher only when more than one is listed and never
 * auto-selects a language that is not; {@code en} is always present. Same
 * activation story as everything else here: env + recreate, no rebuild.
 */
@RestController
@RequestMapping("/auth")
public class SiteConfigController {

    private final String matomoUrl;
    private final String matomoSiteId;
    private final int matomoGoodsDimension;
    private final String stripePublishableKey;
    private final String metaPixelId;
    private final String googleClientId;
    private final String placesApiKey;
    private final String socialFacebookUrl;
    private final String socialInstagramUrl;
    private final String socialTiktokUrl;
    private final String socialYoutubeUrl;
    private final String socialXUrl;
    private final List<String> i18nLanguages;

    public SiteConfigController(
            @Value("${litemall.tracking.matomo.base-url:}") String matomoUrl,
            @Value("${litemall.tracking.matomo.site-id:}") String matomoSiteId,
            @Value("${litemall.tracking.matomo.goods-dimension:1}") int matomoGoodsDimension,
            @Value("${litemall.stripe.publishable-key:}") String stripePublishableKey,
            @Value("${litemall.meta.pixel-id:}") String metaPixelId,
            @Value("${litemall.google.client-id:}") String googleClientId,
            @Value("${litemall.places.api-key:}") String placesApiKey,
            @Value("${litemall.social.facebook-url:}") String socialFacebookUrl,
            @Value("${litemall.social.instagram-url:}") String socialInstagramUrl,
            @Value("${litemall.social.tiktok-url:}") String socialTiktokUrl,
            @Value("${litemall.social.youtube-url:}") String socialYoutubeUrl,
            @Value("${litemall.social.x-url:}") String socialXUrl,
            @Value("${litemall.i18n.languages:en}") String i18nLanguages) {
        this.matomoUrl = blankToNull(matomoUrl);
        this.matomoSiteId = blankToNull(matomoSiteId);
        this.matomoGoodsDimension = matomoGoodsDimension;
        this.stripePublishableKey = blankToNull(stripePublishableKey);
        this.metaPixelId = blankToNull(metaPixelId);
        this.googleClientId = blankToNull(googleClientId);
        this.placesApiKey = blankToNull(placesApiKey);
        this.socialFacebookUrl = blankToNull(socialFacebookUrl);
        this.socialInstagramUrl = blankToNull(socialInstagramUrl);
        this.socialTiktokUrl = blankToNull(socialTiktokUrl);
        this.socialYoutubeUrl = blankToNull(socialYoutubeUrl);
        this.socialXUrl = blankToNull(socialXUrl);
        this.i18nLanguages = parseLanguages(i18nLanguages);
    }

    @GetMapping("/site-config")
    public Map<String, Object> siteConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("matomoUrl", matomoUrl);
        data.put("matomoSiteId", matomoSiteId);
        data.put("matomoGoodsDimension", matomoGoodsDimension);
        data.put("stripePublishableKey", stripePublishableKey);
        data.put("metaPixelId", metaPixelId);
        // Wave 16: both are public-by-design ids/keys on the same null-gated
        // terms — absent env ⇒ null ⇒ the SPA renders no Google button and
        // plain address fields.
        data.put("googleClientId", googleClientId);
        data.put("placesApiKey", placesApiKey);
        data.put("socialFacebookUrl", socialFacebookUrl);
        data.put("socialInstagramUrl", socialInstagramUrl);
        data.put("socialTiktokUrl", socialTiktokUrl);
        data.put("socialYoutubeUrl", socialYoutubeUrl);
        data.put("socialXUrl", socialXUrl);
        data.put("i18nLanguages", i18nLanguages);
        return ApiResponse.ok(data);
    }

    /**
     * "en, sv,DA" → ["en","sv","da"]; blanks dropped, duplicates collapsed, and
     * {@code en} (the bundled default + fallback) always first. Unknown codes are
     * passed through — the SPA owns the supported set and ignores what it
     * cannot serve, so a typo here is harmless rather than a boot failure.
     */
    static List<String> parseLanguages(String csv) {
        List<String> out = new ArrayList<>();
        out.add("en");
        if (csv != null) {
            for (String raw : csv.split(",")) {
                String code = raw.trim().toLowerCase(Locale.ROOT);
                if (!code.isEmpty() && !out.contains(code)) {
                    out.add(code);
                }
            }
        }
        return List.copyOf(out);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
