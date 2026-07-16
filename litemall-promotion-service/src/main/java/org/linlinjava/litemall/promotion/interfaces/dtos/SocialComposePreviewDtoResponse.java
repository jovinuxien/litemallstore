package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the gateway-admin "Promote" composer dialog needs for one goods:
 * a templated caption to prefill the editor, the media candidates, and
 * per-platform availability (enabled flags + the TikTok video gate). Contract
 * documented in {@code docs/handoff-social-composer.md}.
 */
@Getter
@Builder
public class SocialComposePreviewDtoResponse {

    private final Integer goodsId;
    private final String goodsName;
    /** Current retail price (already the deal price while a deal is live). */
    private final BigDecimal price;
    /** Live flash-deal price, null when no deal is live on this goods. */
    private final BigDecimal dealPrice;
    private final Integer dealId;
    /** Prefill for the caption editor (English template; admin edits freely). */
    private final String caption;
    /** Candidate images (pic_url first, then gallery). */
    private final List<String> images;
    /** The goods' product video, null when it has none (gates TikTok). */
    private final String videoUrl;
    private final List<PlatformDto> platforms;

    @Getter
    @Builder
    public static class PlatformDto {

        /** Contract value: {@code meta_fb | meta_ig | tiktok}. */
        private final String platform;
        private final String displayName;
        /** Adapter config flag; posting to a disabled platform yields a failed ledger row. */
        private final boolean enabled;
        /** Media gate: false when the goods lacks what the platform requires. */
        private final boolean available;
        /** Human-readable reason when {@code available=false}. */
        private final String reason;
        /** UTM-tagged share URL this platform's post will carry. */
        private final String shareUrl;
    }
}
