package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationPinkAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationPinkStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LitemallCombinationPinkRepository {

    Optional<LitemallCombinationPinkAggregate> findById(LitemallCombinationPinkId pinkId);

    /** All slots of a group (leader first), identified by the leader slot id. */
    List<LitemallCombinationPinkAggregate> findGroup(LitemallCombinationPinkId headId);

    /** Member count of a group including the leader. */
    int countGroup(LitemallCombinationPinkId headId);

    /** Every slot a user holds, newest first (my groups). */
    List<LitemallCombinationPinkAggregate> findByUser(LitemallUserId userId);

    /** Pending leader slots whose group expired before {@code now} (sweep input). */
    List<LitemallCombinationPinkAggregate> findExpiredPendingLeaders(LocalDateTime now);

    /** Leader slots, optionally scoped to a campaign and/or status (admin monitoring). */
    List<LitemallCombinationPinkAggregate> findLeaders(LitemallCombinationId combinationId,
                                                       LitemallCombinationPinkStatus status);

    void add(LitemallCombinationPinkAggregate pink);

    void update(LitemallCombinationPinkAggregate pink);
}
