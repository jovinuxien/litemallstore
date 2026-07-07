package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;

public class LitemallGroupExpiredEvent extends LitemallDomainEvent {

    private final LitemallCombinationPinkId groupPinkId;
    private final LitemallCombinationId combinationId;
    private final int memberCount;

    public LitemallGroupExpiredEvent(LitemallCombinationPinkId groupPinkId,
                                     LitemallCombinationId combinationId, int memberCount) {
        super("GROUP_EXPIRED");
        this.groupPinkId = groupPinkId;
        this.combinationId = combinationId;
        this.memberCount = memberCount;
    }

    public LitemallCombinationPinkId getGroupPinkId() { return groupPinkId; }
    public LitemallCombinationId getCombinationId() { return combinationId; }
    public int getMemberCount() { return memberCount; }
}
