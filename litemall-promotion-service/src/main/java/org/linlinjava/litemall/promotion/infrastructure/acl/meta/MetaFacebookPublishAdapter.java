package org.linlinjava.litemall.promotion.infrastructure.acl.meta;

import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.infrastructure.acl.meta.dto.MetaObjectResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.SocialProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Anti-corruption layer publishing to the Facebook Page: photo post when the
 * composer picked an image, plain feed post (message + link preview) otherwise.
 * Fail-soft per the {@link SocialPublishPort} contract — disabled, unconfigured,
 * or any Graph error becomes a failed result (→ failed ledger row + WARN),
 * never an exception.
 */
@Component
public class MetaFacebookPublishAdapter implements SocialPublishPort {

    private static final Logger logger = LoggerFactory.getLogger(MetaFacebookPublishAdapter.class);

    private final MetaGraphClient metaGraphClient;
    private final SocialProperties properties;

    public MetaFacebookPublishAdapter(MetaGraphClient metaGraphClient, SocialProperties properties) {
        this.metaGraphClient = metaGraphClient;
        this.properties = properties;
    }

    @Override
    public LitemallSocialPlatform platform() {
        return LitemallSocialPlatform.META_FB;
    }

    @Override
    public boolean isEnabled() {
        return properties.getMeta().isEnabled();
    }

    @Override
    public SocialPublishResult publish(SocialPublishCommand command) {
        SocialProperties.Meta meta = properties.getMeta();
        if (!meta.isEnabled()) {
            return SocialPublishResult.fail(
                    "Meta adapter disabled (litemall.promotion.social.meta.enabled=false)");
        }
        if (!StringUtils.hasText(meta.getPageId()) || !StringUtils.hasText(meta.getPageAccessToken())) {
            return SocialPublishResult.fail("Meta page credentials not configured (page-id / page-access-token)");
        }
        try {
            MetaObjectResponse response;
            if (StringUtils.hasText(command.imageUrl())) {
                // Photo posts have no link field — the UTM share link rides the caption.
                String caption = StringUtils.hasText(command.linkUrl())
                        ? command.caption() + "\n\n" + command.linkUrl()
                        : command.caption();
                response = metaGraphClient.publishPagePhoto(
                        meta.getPageId(), command.imageUrl(), caption, meta.getPageAccessToken());
            } else {
                response = metaGraphClient.publishPageFeed(
                        meta.getPageId(), command.caption(), command.linkUrl(), meta.getPageAccessToken());
            }
            if (response == null || !StringUtils.hasText(response.bestExternalId())) {
                return SocialPublishResult.fail("Graph API returned no post id");
            }
            return SocialPublishResult.ok(response.bestExternalId());
        } catch (Exception e) {
            String message = MetaErrors.sanitize(e.getMessage());
            logger.warn("Facebook publish failed for goods {}: {}", command.goodsId(), message);
            return SocialPublishResult.fail("Graph API error: " + message);
        }
    }
}
