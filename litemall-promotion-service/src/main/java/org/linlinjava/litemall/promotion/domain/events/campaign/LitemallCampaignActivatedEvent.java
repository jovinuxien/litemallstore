package org.linlinjava.litemall.promotion.domain.events.campaign;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;

public class LitemallCampaignActivatedEvent extends LitemallDomainEvent {

    private final Integer campaignId;

    public LitemallCampaignActivatedEvent(LitemallCampaignId campaignId) {
        super("CAMPAIGN_ACTIVATED");
        this.campaignId = campaignId != null ? campaignId.getId() : null;
    }

    public Integer getCampaignId() { return campaignId; }
}
