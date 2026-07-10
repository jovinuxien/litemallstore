package org.linlinjava.litemall.promotion.domain.service;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.AudienceMember;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CampaignBudget;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.service.targeting.AudienceSelector;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pure targeting rules for a campaign: select the audience from a customer
 * population via the {@link AudienceSelector} port, apply the campaign's
 * {@link CampaignBudget} cap (Katsov §3.6.2), and record the assignment on the
 * aggregate. Holds no infrastructure — the application service supplies the
 * already-fetched statistics and persists/publishes the outcome.
 */
@Service
public class LitemallCampaignTargetingDomainService {

    private final AudienceSelector audienceSelector;

    public LitemallCampaignTargetingDomainService(AudienceSelector audienceSelector) {
        this.audienceSelector = audienceSelector;
    }

    /**
     * Evaluate a campaign against a statistics population, returning the
     * budget-capped target audience and stamping {@code assignedCount} on the
     * aggregate. The caller persists the campaign and publishes the assignment.
     *
     * @throws IllegalStateException if the campaign is not currently evaluable
     */
    public List<AudienceMember> selectAudience(LitemallPromotionCampaignAggregate campaign,
                                               List<CustomerStatistics> population,
                                               LocalDateTime now) {
        if (!campaign.canEvaluate(now)) {
            throw new IllegalStateException(
                    "Campaign " + (campaign.getCampaignId() != null ? campaign.getCampaignId().getId() : "?")
                            + " is not active/in-window and cannot be evaluated");
        }

        List<AudienceMember> matched = audienceSelector.select(campaign.getCriteria(), population, now);

        CampaignBudget budget = campaign.getBudget() != null ? campaign.getBudget() : CampaignBudget.uncapped();
        int cap = budget.capAudience(matched.size());
        List<AudienceMember> audience = cap < matched.size() ? matched.subList(0, cap) : matched;

        campaign.recordAssignment(audience.size());
        return audience;
    }
}
