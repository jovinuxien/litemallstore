package org.linlinjava.litemall.loyalty.domain.model.repositories;

import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSignInAggregate;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.util.List;

public interface LitemallSignInRepository {

    /**
     * Persist a new sign-in record to litemall_user_sign.
     */
    void add(LitemallSignInAggregate signIn);

    /**
     * Check whether the user has already signed in today.
     */
    boolean hasTodaySigned(LitemallUserId userId);

    /**
     * Retrieve all sign-in records for a user.
     */
    List<LitemallSignInAggregate> findByUserId(LitemallUserId userId);

    /**
     * Count total sign-in days for a user.
     */
    int countByUserId(LitemallUserId userId);
}
