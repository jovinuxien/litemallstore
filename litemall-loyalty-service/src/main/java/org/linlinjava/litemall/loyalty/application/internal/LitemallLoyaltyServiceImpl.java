package org.linlinjava.litemall.loyalty.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallLoyaltyPointsAggregate;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallEarnPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.commands.LitemallSpendPointsCommand;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallPointsRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallPointsId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.enums.LitemallPointsTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Slf4j
@Service
@Transactional
public class LitemallLoyaltyServiceImpl {

    private final LitemallPointsRepository pointsRepository;

    public LitemallLoyaltyServiceImpl(LitemallPointsRepository pointsRepository) {
        this.pointsRepository = pointsRepository;
    }

    /**
     * Earn points for a user.
     * 1. Get current points balance from litemall_user.integral
     * 2. Add record to litemall_user_integral_record
     * 3. Update litemall_user.integral
     */
    public LitemallLoyaltyPointsAggregate earnPoints(LitemallEarnPointsCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        int currentBalance = getBalanceSafe(userId);
        int newBalance = currentBalance + command.getPoints();

        LitemallPointsId recordId = new LitemallPointsId(generateRecordId());

        pointsRepository.addRecord(
                recordId,
                userId,
                command.getPoints(),
                newBalance,
                command.getTitle(),
                command.getLinkId(),
                command.getLinkType(),
                LitemallPointsTransactionType.EARN_ORDER.getMark()
        );
        pointsRepository.updateUserBalance(userId, newBalance);

        LitemallLoyaltyPointsAggregate result = new LitemallLoyaltyPointsAggregate();
        result.setPointsId(recordId);
        result.setUserId(userId);
        result.setBalance(newBalance);
        result.setChange(command.getPoints());
        result.setTitle(command.getTitle());
        result.setLinkId(command.getLinkId());
        result.setLinkType(command.getLinkType());

        log.info("Points earned: userId={}, points={}, newBalance={}", command.getUserId(), command.getPoints(), newBalance);
        return result;
    }

    /**
     * Spend points for a user.
     * Validates balance, creates a negative record, updates balance.
     */
    public LitemallLoyaltyPointsAggregate spendPoints(LitemallSpendPointsCommand command) {
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        int currentBalance = getBalanceSafe(userId);

        if (currentBalance < command.getPoints()) {
            throw new IllegalStateException(
                    "Insufficient points. Available: " + currentBalance + ", requested: " + command.getPoints());
        }

        int newBalance = currentBalance - command.getPoints();
        LitemallPointsId recordId = new LitemallPointsId(generateRecordId());

        pointsRepository.addRecord(
                recordId,
                userId,
                -command.getPoints(),
                newBalance,
                command.getTitle(),
                command.getLinkId(),
                null,
                LitemallPointsTransactionType.SPEND_ORDER.getMark()
        );
        pointsRepository.updateUserBalance(userId, newBalance);

        LitemallLoyaltyPointsAggregate result = new LitemallLoyaltyPointsAggregate();
        result.setPointsId(recordId);
        result.setUserId(userId);
        result.setBalance(newBalance);
        result.setChange(-command.getPoints());
        result.setTitle(command.getTitle());
        result.setLinkId(command.getLinkId());

        log.info("Points spent: userId={}, points={}, newBalance={}", command.getUserId(), command.getPoints(), newBalance);
        return result;
    }

    /**
     * Get balance, defaulting to 0 if null.
     */
    public int getBalanceSafe(LitemallUserId userId) {
        Integer balance = pointsRepository.getBalance(userId);
        return balance == null ? 0 : balance;
    }

    /**
     * Simple record ID generator (replace with a proper sequence in production).
     */
    private int generateRecordId() {
        return Math.abs(new Random().nextInt(Integer.MAX_VALUE - 1)) + 1;
    }
}
