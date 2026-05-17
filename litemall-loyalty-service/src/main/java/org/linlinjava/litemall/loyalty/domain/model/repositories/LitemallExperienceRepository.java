package org.linlinjava.litemall.loyalty.domain.model.repositories;

import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallExperienceId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

public interface LitemallExperienceRepository {

    /**
     * Get current experience balance for a user from litemall_user.experience field.
     */
    Integer getBalance(LitemallUserId userId);

    /**
     * Add a record to litemall_user_experience_record.
     */
    void addRecord(LitemallExperienceId experienceId, LitemallUserId userId, int change, int balance,
                   String title, String linkId, String linkType);

    /**
     * Update user's experience field in litemall_user table.
     */
    void updateUserExperience(LitemallUserId userId, int newBalance);
}