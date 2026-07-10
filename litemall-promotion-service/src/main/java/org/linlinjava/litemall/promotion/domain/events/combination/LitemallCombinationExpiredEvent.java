package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;

public class LitemallCombinationExpiredEvent extends LitemallDomainEvent {

    private final LitemallCombinationId combinationId;

    public LitemallCombinationExpiredEvent(LitemallCombinationId combinationId) {
        super("COMBINATION_EXPIRED");
        this.combinationId = combinationId;
    }

    public LitemallCombinationId getCombinationId() { return combinationId; }
}
