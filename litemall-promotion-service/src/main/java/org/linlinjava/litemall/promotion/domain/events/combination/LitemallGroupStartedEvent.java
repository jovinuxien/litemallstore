package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallGroupStartedEvent extends LitemallDomainEvent {

    private final LitemallCombinationPinkId pinkId;
    private final LitemallCombinationId combinationId;
    private final LitemallUserId leaderId;

    public LitemallGroupStartedEvent(LitemallCombinationPinkId pinkId,
                                     LitemallCombinationId combinationId,
                                     LitemallUserId leaderId) {
        super("GROUP_STARTED");
        this.pinkId = pinkId;
        this.combinationId = combinationId;
        this.leaderId = leaderId;
    }

    public LitemallCombinationPinkId getPinkId() { return pinkId; }
    public LitemallCombinationId getCombinationId() { return combinationId; }
    public LitemallUserId getLeaderId() { return leaderId; }
}
