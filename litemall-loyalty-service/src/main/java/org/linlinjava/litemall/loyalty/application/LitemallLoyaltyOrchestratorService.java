package org.linlinjava.litemall.loyalty.application;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.loyalty.application.internal.LitemallLevelServiceLayer;
import org.linlinjava.litemall.loyalty.application.internal.LitemallLoyaltyServiceImpl;
import org.linlinjava.litemall.loyalty.application.internal.LitemallSignInServiceLayer;
import org.linlinjava.litemall.loyalty.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallPointsEarnedEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallPointsSpentEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallSignInRewardedEvent;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallLoyaltyPointsAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSignInAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallUserLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallCheckLevelUpCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallEarnExperienceCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallEarnPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallSignInCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallSpendPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@Transactional
public class LitemallLoyaltyOrchestratorService {

    private final LitemallLoyaltyServiceImpl loyaltyService;
    private final LitemallSignInServiceLayer signInService;
    private final LitemallLevelServiceLayer levelService;

    @Autowired
    private LitemallDomainEventPublisher domainEventPublisher;

    public LitemallLoyaltyOrchestratorService(LitemallLoyaltyServiceImpl loyaltyService,
                                               LitemallSignInServiceLayer signInService,
                                               LitemallLevelServiceLayer levelService) {
        this.loyaltyService = loyaltyService;
        this.signInService = signInService;
        this.levelService = levelService;
    }

    public enum LoyaltyAction {
        EARN_POINTS,
        SPEND_POINTS,
        SIGN_IN,
        EARN_EXPERIENCE,
        CHECK_LEVEL_UP
    }

    /**
     * Main entry point – dispatches to specific service layers.
     */
    public Object performLoyaltyAction(LoyaltyAction action, Object actionData) {
        switch (action) {
            case EARN_POINTS:
                return handleEarnPoints((LitemallEarnPointsCommand) actionData);
            case SPEND_POINTS:
                return handleSpendPoints((LitemallSpendPointsCommand) actionData);
            case SIGN_IN:
                return handleSignIn((LitemallSignInCommand) actionData);
            case EARN_EXPERIENCE:
                return handleEarnExperience((LitemallEarnExperienceCommand) actionData);
            case CHECK_LEVEL_UP:
                return handleCheckLevelUp((LitemallCheckLevelUpCommand) actionData);
            default:
                throw new IllegalArgumentException("Unsupported loyalty action: " + action);
        }
    }

    // =========================================================================
    // ACTION HANDLERS
    // =========================================================================

    private LitemallLoyaltyPointsAggregate handleEarnPoints(LitemallEarnPointsCommand command) {
        LitemallLoyaltyPointsAggregate result = loyaltyService.earnPoints(command);
        domainEventPublisher.publish(new LitemallPointsEarnedEvent(
                command.getUserId(), command.getPoints(), result.getBalance(),
                command.getTitle(), command.getLinkId()));
        return result;
    }

    private LitemallLoyaltyPointsAggregate handleSpendPoints(LitemallSpendPointsCommand command) {
        LitemallLoyaltyPointsAggregate result = loyaltyService.spendPoints(command);
        domainEventPublisher.publish(new LitemallPointsSpentEvent(
                command.getUserId(), command.getPoints(), result.getBalance(), command.getTitle()));
        return result;
    }

    private LitemallSignInAggregate handleSignIn(LitemallSignInCommand command) {
        LitemallSignInAggregate result = signInService.signIn(command);
        if (result.getIntegral() > 0) {
            domainEventPublisher.publish(new LitemallSignInRewardedEvent(
                    command.getUserId(), result.getIntegral(), result.getSignDate()));
        }
        return result;
    }

    private int handleEarnExperience(LitemallEarnExperienceCommand command) {
        int newBalance = levelService.earnExperience(command);
        // Trigger level-up check after earning XP
        levelService.checkAndUpgrade(new LitemallUserId(command.getUserId()));
        return newBalance;
    }

    private Optional<LitemallUserLevelAggregate> handleCheckLevelUp(LitemallCheckLevelUpCommand command) {
        return levelService.checkAndUpgrade(new LitemallUserId(command.getUserId()));
    }

    // =========================================================================
    // CONVENIENCE METHODS
    // =========================================================================

    public LitemallLoyaltyPointsAggregate earnPoints(LitemallEarnPointsCommand command) {
        return (LitemallLoyaltyPointsAggregate) performLoyaltyAction(LoyaltyAction.EARN_POINTS, command);
    }

    public LitemallLoyaltyPointsAggregate spendPoints(LitemallSpendPointsCommand command) {
        return (LitemallLoyaltyPointsAggregate) performLoyaltyAction(LoyaltyAction.SPEND_POINTS, command);
    }

    public LitemallSignInAggregate signIn(LitemallSignInCommand command) {
        return (LitemallSignInAggregate) performLoyaltyAction(LoyaltyAction.SIGN_IN, command);
    }

    @SuppressWarnings("unchecked")
    public Optional<LitemallUserLevelAggregate> checkLevelUp(LitemallCheckLevelUpCommand command) {
        return (Optional<LitemallUserLevelAggregate>) performLoyaltyAction(LoyaltyAction.CHECK_LEVEL_UP, command);
    }
}
