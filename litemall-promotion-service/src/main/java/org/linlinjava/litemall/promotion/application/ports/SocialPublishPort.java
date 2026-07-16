package org.linlinjava.litemall.promotion.application.ports;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;

/**
 * Outbound port for publishing one post to one social platform (Wave 6).
 * Implemented per platform by the ACL adapters under
 * {@code infrastructure/acl/meta} and {@code infrastructure/acl/tiktok};
 * application code holds the full {@code List<SocialPublishPort>} and selects
 * by {@link #platform()}.
 *
 * <p><b>Fail-soft contract:</b> {@link #publish} NEVER throws — a disabled
 * adapter, missing credentials, missing required media, or any transport/API
 * error comes back as {@code SocialPublishResult.fail(...)} with a
 * human-readable error for the ledger row. The caller records the outcome; a
 * platform outage must never 5xx an admin request or kill the auto-post sweep
 * (the Mautic adapter conventions, exactly).
 */
public interface SocialPublishPort {

    /** The single platform this adapter publishes to. */
    LitemallSocialPlatform platform();

    /** Config flag ({@code litemall.promotion.social.<platform>.enabled}); false by default. */
    boolean isEnabled();

    SocialPublishResult publish(SocialPublishCommand command);

    /**
     * @param imageUrl image to attach (meta_fb optional — falls back to a link post;
     *                 meta_ig required); ignored by tiktok
     * @param videoUrl the goods' video (tiktok required); ignored by meta_fb/meta_ig
     * @param linkUrl  UTM-tagged share URL (woven into the caption where the
     *                 platform has no link field)
     */
    record SocialPublishCommand(Integer goodsId, String caption, String imageUrl,
                                String videoUrl, String linkUrl) {
    }

    record SocialPublishResult(boolean success, String externalPostId, String error) {

        public static SocialPublishResult ok(String externalPostId) {
            return new SocialPublishResult(true, externalPostId, null);
        }

        public static SocialPublishResult fail(String error) {
            return new SocialPublishResult(false, null, error);
        }
    }
}
