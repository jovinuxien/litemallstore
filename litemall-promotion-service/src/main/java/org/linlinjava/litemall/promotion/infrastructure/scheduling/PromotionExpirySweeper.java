package org.linlinjava.litemall.promotion.infrastructure.scheduling;

import org.linlinjava.litemall.promotion.application.internal.LitemallCombinationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic expiry sweep for group-buy participation: fails pending groups past
 * their fill deadline and expires ACTIVE campaigns past their end time. The
 * cadence comes from {@code litemall.promotion.groupon.sweep-fixed-delay-ms}
 * (see {@link org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionGrouponProperties}).
 */
@Component
public class PromotionExpirySweeper {

    private static final Logger logger = LoggerFactory.getLogger(PromotionExpirySweeper.class);

    private final LitemallCombinationServiceImpl combinationService;

    public PromotionExpirySweeper(LitemallCombinationServiceImpl combinationService) {
        this.combinationService = combinationService;
    }

    @Scheduled(fixedDelayString = "${litemall.promotion.groupon.sweep-fixed-delay-ms:60000}")
    public void sweep() {
        try {
            combinationService.expireOverdue();
        } catch (Exception e) {
            logger.error("Promotion expiry sweep failed", e);
        }
    }
}
