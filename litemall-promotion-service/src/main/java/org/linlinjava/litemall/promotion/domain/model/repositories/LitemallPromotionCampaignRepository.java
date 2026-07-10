package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;

import java.util.List;
import java.util.Optional;

public interface LitemallPromotionCampaignRepository {

    Optional<LitemallPromotionCampaignAggregate> findById(LitemallCampaignId campaignId);

    /** Currently ACTIVE campaigns. */
    List<LitemallPromotionCampaignAggregate> findActive();

    /** Admin listing of all non-deleted campaigns (newest first). */
    List<LitemallPromotionCampaignAggregate> findAll();

    void save(LitemallPromotionCampaignAggregate campaign);
}
