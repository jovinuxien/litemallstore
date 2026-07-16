package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed, environment-overridable settings for the Wave-6 social posting
 * vertical. Bound from {@code litemall.promotion.social.*}.
 *
 * <p>Both platform adapters are DISABLED by default and every credential comes
 * via {@code ${ENV:default}} — the service boots, the composer previews, and
 * the ledger records honest {@code failed} rows without any Meta/TikTok
 * registration (the user-side prerequisite that may land mid-wave). Auto-post
 * is a separate opt-in on top of the platform flags.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.social")
@Getter
@Setter
public class SocialProperties {

    /** Master opt-in for the flash-deal auto-poster (posts to ENABLED platforms only). */
    private boolean autoPostDeals = false;

    /** Auto-poster poll cadence; state-based, so a slow cadence only delays posts. */
    private long autoPostSweepMs = 60000;

    /**
     * Customer-storefront origin used by the UTM link-builder
     * ({@code <share-base-url>/product/<goodsId>?utm_*}); the gateway-api SPA
     * origin in dev, the public storefront domain in prod.
     */
    private String shareBaseUrl = "http://localhost:9000";

    private final Meta meta = new Meta();
    private final Tiktok tiktok = new Tiktok();

    /** Meta Graph API — ONE business app serving both the FB Page and the IG business account. */
    @Getter
    @Setter
    public static class Meta {

        /** When false both Meta adapters fail-soft with a "disabled" ledger error. */
        private boolean enabled = false;

        /** Graph API root incl. version; override to pin a version or point at a test double. */
        private String baseUrl = "https://graph.facebook.com/v19.0";

        /** The Facebook Page id posts are published to. */
        private String pageId;

        /**
         * Long-lived PAGE access token (needs pages_manage_posts +
         * instagram_content_publish); renewal runbook in adr-social-publishing.md.
         */
        private String pageAccessToken;

        /** The Instagram business-account user id linked to the page. */
        private String igUserId;
    }

    /** TikTok Content Posting API — video-only platform. */
    @Getter
    @Setter
    public static class Tiktok {

        /** When false the TikTok adapter fail-softs with a "disabled" ledger error. */
        private boolean enabled = false;

        private String baseUrl = "https://open.tiktokapis.com";

        /** OAuth access token with the video.publish scope. */
        private String accessToken;

        /**
         * Posted video visibility. SELF_ONLY is the only level TikTok allows an
         * UNAUDITED app to use — switch to PUBLIC_TO_EVERYONE after the app
         * passes TikTok's audit (adr-social-publishing.md).
         */
        private String privacyLevel = "SELF_ONLY";
    }
}
