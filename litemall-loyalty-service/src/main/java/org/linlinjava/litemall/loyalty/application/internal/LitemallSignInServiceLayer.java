package org.linlinjava.litemall.loyalty.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSignInAggregate;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallEarnPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallSignInCommand;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallSignInRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
public class LitemallSignInServiceLayer {

    private static final int SIGN_IN_REWARD_POINTS = 10;

    private final LitemallSignInRepository signInRepository;
    private final LitemallLoyaltyServiceImpl loyaltyService;

    public LitemallSignInServiceLayer(LitemallSignInRepository signInRepository,
                                      LitemallLoyaltyServiceImpl loyaltyService) {
        this.signInRepository = signInRepository;
        this.loyaltyService = loyaltyService;
    }

    /**
     * Process a user's daily sign-in.
     * 1. Check if already signed in today
     * 2. If not: calculate reward, create sign record, trigger earnPoints
     * 3. Return reward info as a sign-in aggregate
     */
    public LitemallSignInAggregate signIn(LitemallSignInCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());

        boolean alreadySigned = signInRepository.hasTodaySigned(userId);
        if (alreadySigned) {
            log.info("User {} already signed in today.", command.getUserId());
            // Return a sentinel aggregate indicating already-signed
            LitemallSignInAggregate sentinel = new LitemallSignInAggregate();
            sentinel.setUserId(userId);
            sentinel.setIntegral(0);
            sentinel.setSignDate(LocalDateTime.now());
            return sentinel;
        }

        // Calculate reward (fixed 10 pts for now; can be made dynamic)
        int reward = SIGN_IN_REWARD_POINTS;
        LocalDateTime now = LocalDateTime.now();

        LitemallSignInAggregate signIn = LitemallSignInAggregate.create(userId, reward, now);
        signInRepository.add(signIn);

        // Trigger points earning
        LitemallEarnPointsCommand earnCommand = new LitemallEarnPointsCommand(
                command.getUserId(), reward, "Daily sign-in reward", null, "sign");
        loyaltyService.earnPoints(earnCommand);

        log.info("Sign-in processed: userId={}, reward={}", command.getUserId(), reward);
        return signIn;
    }

    public int getTotalSignDays(LitemallUserId userId) {
        return signInRepository.countByUserId(userId);
    }
}
