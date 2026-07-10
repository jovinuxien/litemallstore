package org.linlinjava.litemall.promotion.infrastructure.acl.mautic;

import org.linlinjava.litemall.promotion.application.ports.CampaignDeliveryPort;
import org.linlinjava.litemall.promotion.domain.events.campaign.PromotionTargetedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a targeting decision into outbound delivery: listens for
 * {@link PromotionTargetedEvent} at {@link TransactionPhase#AFTER_COMMIT} (so the
 * campaign evaluation has durably committed; {@code fallbackExecution = true}
 * keeps it firing for non-transactional publishes) and hands the audience to the
 * {@link CampaignDeliveryPort} (Mautic ACL).
 *
 * <p>Lives in {@code infrastructure/acl/mautic} — not in the domain event-handler
 * — so the external delivery client stays out of {@code domain/}; the listener
 * itself depends only on the application port.
 */
@Component
public class MauticTargetingDeliveryListener {

    private static final Logger logger = LoggerFactory.getLogger(MauticTargetingDeliveryListener.class);

    private final CampaignDeliveryPort campaignDeliveryPort;

    public MauticTargetingDeliveryListener(CampaignDeliveryPort campaignDeliveryPort) {
        this.campaignDeliveryPort = campaignDeliveryPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPromotionTargeted(PromotionTargetedEvent event) {
        if (event == null) {
            return;
        }
        logger.info("Delivering targeting decision: campaign={}, audienceSize={}",
                event.getCampaignId(), event.getAudienceSize());
        campaignDeliveryPort.deliverToAudience(
                event.getCampaignId(),
                event.getAudienceUserIds(),
                event.getLinkedPromotionType(),
                event.getLinkedPromotionId());
    }
}
