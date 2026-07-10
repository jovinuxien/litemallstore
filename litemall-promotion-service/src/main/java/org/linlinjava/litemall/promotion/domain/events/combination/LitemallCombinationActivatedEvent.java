package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;

public class LitemallCombinationActivatedEvent extends LitemallDomainEvent {

    private final LitemallCombinationId combinationId;

    public LitemallCombinationActivatedEvent(LitemallCombinationId combinationId) {
        super("COMBINATION_ACTIVATED");
        this.combinationId = combinationId;
    }

    public LitemallCombinationId getCombinationId() { return combinationId; }
}
