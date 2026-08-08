package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;

import java.util.List;

public class LitemallGroupCompletedEvent extends LitemallDomainEvent {

    private final LitemallCombinationPinkId groupPinkId;
    private final LitemallCombinationId combinationId;
    private final int memberCount;
    /**
     * Wave 21 (ADDITIVE): slot ids of every participant of the completed
     * group, the leader's own slot included. Order uses these to find the
     * local orders affected by the group outcome (each order stores its
     * buyer's pinkId). Slots released before completion are NOT listed.
     */
    private final List<Integer> memberPinkIds;

    public LitemallGroupCompletedEvent(LitemallCombinationPinkId groupPinkId,
                                       LitemallCombinationId combinationId, int memberCount,
                                       List<Integer> memberPinkIds) {
        super("GROUP_COMPLETED");
        this.groupPinkId = groupPinkId;
        this.combinationId = combinationId;
        this.memberCount = memberCount;
        this.memberPinkIds = memberPinkIds != null ? List.copyOf(memberPinkIds) : List.of();
    }

    public LitemallCombinationPinkId getGroupPinkId() { return groupPinkId; }
    public LitemallCombinationId getCombinationId() { return combinationId; }
    public int getMemberCount() { return memberCount; }
    public List<Integer> getMemberPinkIds() { return memberPinkIds; }
}
