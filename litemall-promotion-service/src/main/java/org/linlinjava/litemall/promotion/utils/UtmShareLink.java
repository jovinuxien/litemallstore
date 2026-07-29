package org.linlinjava.litemall.promotion.utils;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Server-side half of the Wave-6 shared UTM link convention (the gateway-api
 * SPA implements the consuming half):
 *
 * <pre>{@code <share-base-url>/product/<goodsId>?utm_source=facebook|instagram|tiktok
 *                                     &utm_medium=social&utm_campaign=<slug>}</pre>
 *
 * {@code utm_source} comes from the posting platform; {@code utm_medium} is
 * always {@code social}; {@code utm_campaign} slugs are {@code deal-<dealId>}
 * for flash-deal posts and {@code goods-<goodsId>} otherwise, so Matomo
 * campaign reports break down by promotion. The same query-param style is used
 * by the affiliate {@code ?invite=} links — both must survive SPA routing
 * (gateway-api's Wave-6 task verifies the combination).
 */
public final class UtmShareLink {

    public static final String UTM_MEDIUM_SOCIAL = "social";

    private UtmShareLink() {
        // Utility class — no instances
    }

    /** Product share URL for a post on the given platform. */
    public static String productUrl(String shareBaseUrl, Integer goodsId,
                                    LitemallSocialPlatform platform, String campaignSlug) {
        String base = shareBaseUrl != null && shareBaseUrl.endsWith("/")
                ? shareBaseUrl.substring(0, shareBaseUrl.length() - 1)
                : shareBaseUrl;
        return base + "/product/" + goodsId
                + "?utm_source=" + encode(platform.getUtmSource())
                + "&utm_medium=" + UTM_MEDIUM_SOCIAL
                + "&utm_campaign=" + encode(campaignSlug);
    }

    /** Campaign slug for a flash-deal post: one campaign per deal activation window. */
    public static String dealSlug(Integer dealId) {
        return "deal-" + dealId;
    }

    /** Campaign slug for a plain goods post. */
    public static String goodsSlug(Integer goodsId) {
        return "goods-" + goodsId;
    }

    /**
     * Campaign slug for a Wave-12 scheduled category campaign's posts. Doubles
     * as the durable campaign↔post correlation: draft rows carry it in
     * {@code link_url}, which is how the schedule tick finds a campaign's
     * drafts at activation (the V42 ledger has no campaign column).
     */
    public static String campaignSlug(Integer campaignId) {
        return "campaign-" + campaignId;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
