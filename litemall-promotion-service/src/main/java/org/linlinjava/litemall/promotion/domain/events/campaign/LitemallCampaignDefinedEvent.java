package org.linlinjava.litemall.promotion.domain.events.campaign;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;

public class LitemallCampaignDefinedEvent extends LitemallDomainEvent {

    private final Integer campaignId;
    private final String name;

    public LitemallCampaignDefinedEvent(LitemallCampaignId campaignId, String name) {
        super("CAMPAIGN_DEFINED");
        this.campaignId = campaignId != null ? campaignId.getId() : null;
        this.name = name;
    }

    public Integer getCampaignId() { return campaignId; }
    public String getName() { return name; }
}
