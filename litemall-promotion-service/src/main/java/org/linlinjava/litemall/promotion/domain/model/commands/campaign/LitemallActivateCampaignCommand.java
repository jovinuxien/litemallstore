package org.linlinjava.litemall.promotion.domain.model.commands.campaign;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;

/** Admin command to make a DRAFT/PAUSED campaign ACTIVE. */
@Getter
@AllArgsConstructor
public class LitemallActivateCampaignCommand {
    private final LitemallCampaignId campaignId;
}
