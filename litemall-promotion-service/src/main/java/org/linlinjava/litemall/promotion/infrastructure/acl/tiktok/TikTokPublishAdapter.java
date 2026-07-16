package org.linlinjava.litemall.promotion.infrastructure.acl.tiktok;

import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.infrastructure.acl.tiktok.dto.TikTokPublishResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.SocialProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Anti-corruption layer publishing to TikTok via the Content Posting API's
 * direct-post flow with {@code PULL_FROM_URL} (TikTok fetches the goods' video
 * itself). TikTok is video-only — a goods without a video fails fast with a
 * clear ledger error (the composer greys the platform out up front). TikTok
 * signals logical failures inside a 200 body ({@code error.code != "ok"}), so
 * the adapter checks the envelope, not just the transport. Fail-soft per the
 * {@link SocialPublishPort} contract.
 */
@Component
public class TikTokPublishAdapter implements SocialPublishPort {

    private static final Logger logger = LoggerFactory.getLogger(TikTokPublishAdapter.class);

    private final TikTokContentClient tikTokContentClient;
    private final SocialProperties properties;

    public TikTokPublishAdapter(TikTokContentClient tikTokContentClient, SocialProperties properties) {
        this.tikTokContentClient = tikTokContentClient;
        this.properties = properties;
    }

    @Override
    public LitemallSocialPlatform platform() {
        return LitemallSocialPlatform.TIKTOK;
    }

    @Override
    public boolean isEnabled() {
        return properties.getTiktok().isEnabled();
    }

    @Override
    public SocialPublishResult publish(SocialPublishCommand command) {
        SocialProperties.Tiktok tiktok = properties.getTiktok();
        if (!tiktok.isEnabled()) {
            return SocialPublishResult.fail(
                    "TikTok adapter disabled (litemall.promotion.social.tiktok.enabled=false)");
        }
        if (!StringUtils.hasText(tiktok.getAccessToken())) {
            return SocialPublishResult.fail("TikTok access token not configured");
        }
        if (!StringUtils.hasText(command.videoUrl())) {
            return SocialPublishResult.fail("TikTok requires a video — this goods has none");
        }
        try {
            Map<String, Object> postInfo = new HashMap<>();
            postInfo.put("title", command.caption());
            postInfo.put("privacy_level", tiktok.getPrivacyLevel());
            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("source", "PULL_FROM_URL");
            sourceInfo.put("video_url", command.videoUrl());
            Map<String, Object> body = new HashMap<>();
            body.put("post_info", postInfo);
            body.put("source_info", sourceInfo);

            TikTokPublishResponse response = tikTokContentClient.initVideoPublish(
                    "Bearer " + tiktok.getAccessToken(), body);
            if (response == null) {
                return SocialPublishResult.fail("TikTok API returned an empty response");
            }
            if (!response.isOk()) {
                TikTokPublishResponse.Error error = response.getError();
                return SocialPublishResult.fail("TikTok API error " + error.getCode()
                        + ": " + error.getMessage());
            }
            if (response.getData() == null || !StringUtils.hasText(response.getData().getPublishId())) {
                return SocialPublishResult.fail("TikTok API returned no publish id");
            }
            return SocialPublishResult.ok(response.getData().getPublishId());
        } catch (Exception e) {
            logger.warn("TikTok publish failed for goods {}: {}", command.goodsId(), e.getMessage());
            return SocialPublishResult.fail("TikTok API error: " + e.getMessage());
        }
    }
}
