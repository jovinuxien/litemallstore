package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;

import java.util.List;
import java.util.Optional;

public interface LitemallCombinationRepository {

    Optional<LitemallCombinationAggregate> findById(LitemallCombinationId combinationId);

    /** Customer-visible campaigns: ACTIVE and inside their window. */
    List<LitemallCombinationAggregate> findActive();

    /** Admin listing of all non-deleted campaigns (newest first). */
    List<LitemallCombinationAggregate> findAll();

    void save(LitemallCombinationAggregate combination);
}
