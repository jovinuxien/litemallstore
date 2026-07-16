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
 * Anti-corruption layer publishing to the Instagram business account via the
 * Graph API two-step flow: create a media container for the image + caption,
 * then publish it. Instagram is image-required — a post without an image
 * fails fast with a clear ledger error (the composer's availability flags
 * surface this before posting). Same fail-soft contract as the FB adapter.
 */
@Component
public class MetaInstagramPublishAdapter implements SocialPublishPort {

    private static final Logger logger = LoggerFactory.getLogger(MetaInstagramPublishAdapter.class);

    private final MetaGraphClient metaGraphClient;
    private final SocialProperties properties;

    public MetaInstagramPublishAdapter(MetaGraphClient metaGraphClient, SocialProperties properties) {
        this.metaGraphClient = metaGraphClient;
        this.properties = properties;
    }

    @Override
    public LitemallSocialPlatform platform() {
        return LitemallSocialPlatform.META_IG;
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
        if (!StringUtils.hasText(meta.getIgUserId()) || !StringUtils.hasText(meta.getPageAccessToken())) {
            return SocialPublishResult.fail("Instagram credentials not configured (ig-user-id / page-access-token)");
        }
        if (!StringUtils.hasText(command.imageUrl())) {
            return SocialPublishResult.fail("Instagram requires an image — pick one in the composer");
        }
        try {
            // IG captions carry the UTM link for attribution (not clickable — IG policy).
            String caption = StringUtils.hasText(command.linkUrl())
                    ? command.caption() + "\n\n" + command.linkUrl()
                    : command.caption();
            MetaObjectResponse container = metaGraphClient.createIgMediaContainer(
                    meta.getIgUserId(), command.imageUrl(), caption, meta.getPageAccessToken());
            if (container == null || !StringUtils.hasText(container.getId())) {
                return SocialPublishResult.fail("Graph API returned no IG media-container id");
            }
            MetaObjectResponse published = metaGraphClient.publishIgMedia(
                    meta.getIgUserId(), container.getId(), meta.getPageAccessToken());
            if (published == null || !StringUtils.hasText(published.getId())) {
                return SocialPublishResult.fail("Graph API returned no IG media id on publish");
            }
            return SocialPublishResult.ok(published.getId());
        } catch (Exception e) {
            String message = MetaErrors.sanitize(e.getMessage());
            logger.warn("Instagram publish failed for goods {}: {}", command.goodsId(), message);
            return SocialPublishResult.fail("Graph API error: " + message);
        }
    }
}
