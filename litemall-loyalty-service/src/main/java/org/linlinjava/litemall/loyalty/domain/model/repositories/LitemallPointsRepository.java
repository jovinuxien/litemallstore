package org.linlinjava.litemall.loyalty.domain.model.repositories;

import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallLoyaltyPointsAggregate;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallPointsId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.util.List;

public interface LitemallPointsRepository {

    /**
     * Get current points balance for a user from litemall_user.integral field.
     */
    Integer getBalance(LitemallUserId userId);

    /**
     * Add a record to litemall_user_integral_record.
     */
    void addRecord(LitemallPointsId pointsId, LitemallUserId userId, int change, int balance,
                   String title, String linkId, String linkType, String mark);

    /**
     * Find all points records for a given user.
     */
    List<LitemallLoyaltyPointsAggregate> findRecordsByUserId(LitemallUserId userId);

    /**
     * Sum all positive (earned) entries for a user — total points ever earned.
     */
    int sumPositiveByUserId(LitemallUserId userId);

    /**
     * Update user's integral field in litemall_user table.
     */
    void updateUserBalance(LitemallUserId userId, int newBalance);
}
