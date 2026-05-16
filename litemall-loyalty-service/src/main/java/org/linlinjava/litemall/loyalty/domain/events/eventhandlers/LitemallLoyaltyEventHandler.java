package org.linlinjava.litemall.loyalty.domain.events.eventhandlers;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallExperienceEarnedEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallPointsEarnedEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallPointsSpentEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallSignInRewardedEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallUserLevelUpEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LitemallLoyaltyEventHandler {

    @EventListener
    public void onPointsEarned(LitemallPointsEarnedEvent event) {
        log.info("Event received - POINTS_EARNED: userId={}, pointsEarned={}, newBalance={}",
                event.getUserId(), event.getPointsEarned(), event.getNewBalance());
        // Additional cross-cutting concerns: analytics, notifications, etc.
    }

    @EventListener
    public void onPointsSpent(LitemallPointsSpentEvent event) {
        log.info("Event received - POINTS_SPENT: userId={}, pointsSpent={}, newBalance={}",
                event.getUserId(), event.getPointsSpent(), event.getNewBalance());
    }

    @EventListener
    public void onSignInRewarded(LitemallSignInRewardedEvent event) {
        log.info("Event received - SIGN_IN_REWARDED: userId={}, integralEarned={}, signDate={}",
                event.getUserId(), event.getIntegralEarned(), event.getSignDate());
    }

    @EventListener
    public void onUserLevelUp(LitemallUserLevelUpEvent event) {
        log.info("Event received - USER_LEVEL_UP: userId={}, previousGrade={}, newGrade={}, newLevelId={}",
                event.getUserId(), event.getPreviousGrade(), event.getNewGrade(), event.getNewLevelId());
    }

    @EventListener
    public void onExperienceEarned(LitemallExperienceEarnedEvent event) {
        log.info("Event received - EXPERIENCE_EARNED: userId={}, experienceEarned={}, newBalance={}",
                event.getUserId(), event.getExperienceEarned(), event.getNewBalance());
    }
}
