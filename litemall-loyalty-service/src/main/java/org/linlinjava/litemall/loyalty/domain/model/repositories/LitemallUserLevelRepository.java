package org.linlinjava.litemall.loyalty.domain.model.repositories;

import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSystemLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallUserLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.util.List;
import java.util.Optional;

public interface LitemallUserLevelRepository {

    /**
     * Find the user's current active level record.
     */
    Optional<LitemallUserLevelAggregate> findCurrentByUserId(LitemallUserId userId);

    /**
     * Persist or update a user level record.
     */
    void save(LitemallUserLevelAggregate level);

    /**
     * Retrieve all VIP level definitions from litemall_system_user_level.
     */
    List<LitemallSystemLevelAggregate> findAllSystemLevels();

    /**
     * Find the next level definition that matches the given experience threshold.
     */
    Optional<LitemallSystemLevelAggregate> findNextLevelByExperience(int experience);
}
