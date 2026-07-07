package org.linlinjava.litemall.promotion.domain.model.commands.campaign;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;

/**
 * Admin command to evaluate a campaign: compute its target audience from real
 * customer statistics and produce a promotion assignment (returned + emitted as
 * PromotionTargetedEvent).
 */
@Getter
@AllArgsConstructor
public class LitemallEvaluateCampaignCommand {
    private final LitemallCampaignId campaignId;
}
