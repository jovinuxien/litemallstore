package org.linlinjava.litemall.promotion.domain.service;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.springframework.stereotype.Service;

/**
 * Pure rules for combination (group-buy) campaign definitions. Validates that an
 * offer is internally coherent before it is persisted/activated.
 */
@Service
public class LitemallCombinationDomainService {

    public void validateOffer(LitemallCombinationAggregate combination) {
        if (combination.getGoodsId() == null) {
            throw new IllegalArgumentException("goodsId is required");
        }
        if (combination.getCombinationPrice() == null) {
            throw new IllegalArgumentException("combinationPrice is required");
        }
        if (combination.getRequiredMembers() == null || combination.getRequiredMembers() < 2) {
            throw new IllegalArgumentException("requiredMembers must be at least 2");
        }
        if (combination.getOriginalPrice() != null
                && !combination.getCombinationPrice().isLessThanOrEqualTo(combination.getOriginalPrice())) {
            throw new IllegalArgumentException("combinationPrice must not exceed originalPrice");
        }
        if (combination.getStartTime() != null && combination.getEndTime() != null
                && combination.getEndTime().isBefore(combination.getStartTime())) {
            throw new IllegalArgumentException("endTime must not be before startTime");
        }
    }
}
