package org.linlinjava.litemall.promotion.domain.events.campaign;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;

import java.util.Collections;
import java.util.List;

/**
 * Raised when a campaign evaluation produces a target audience — the promotion
 * assignment. Carries the campaign, the selected user ids, and the linked
 * Phase-1 promo mechanic. In Phase 3 this is the event a Mautic ACL maps to a
 * campaign/segment trigger so a targeting decision actually delivers.
 */
public class PromotionTargetedEvent extends LitemallDomainEvent {

    private final Integer campaignId;
    private final List<Integer> audienceUserIds;
    private final String linkedPromotionType;
    private final Integer linkedPromotionId;

    public PromotionTargetedEvent(LitemallCampaignId campaignId,
                                  List<Integer> audienceUserIds,
                                  String linkedPromotionType,
                                  Integer linkedPromotionId) {
        super("PROMOTION_TARGETED");
        this.campaignId = campaignId != null ? campaignId.getId() : null;
        this.audienceUserIds = audienceUserIds != null ? audienceUserIds : Collections.emptyList();
        this.linkedPromotionType = linkedPromotionType;
        this.linkedPromotionId = linkedPromotionId;
    }

    public Integer getCampaignId() { return campaignId; }
    public List<Integer> getAudienceUserIds() { return audienceUserIds; }
    public int getAudienceSize() { return audienceUserIds.size(); }
    public String getLinkedPromotionType() { return linkedPromotionType; }
    public Integer getLinkedPromotionId() { return linkedPromotionId; }
}
