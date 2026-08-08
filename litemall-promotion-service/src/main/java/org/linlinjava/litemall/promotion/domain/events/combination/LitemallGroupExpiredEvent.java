package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;

import java.util.List;

public class LitemallGroupExpiredEvent extends LitemallDomainEvent {

    private final LitemallCombinationPinkId groupPinkId;
    private final LitemallCombinationId combinationId;
    private final int memberCount;
    /**
     * Wave 21 (ADDITIVE): slot ids failed BY THIS expiry/dissolution — the
     * slots still pending when the group died, the leader's own slot
     * included. Slots released earlier (order cancelled pre-expiry) are NOT
     * listed: their orders were already handled at release time. Order uses
     * these ids to auto-cancel + refund the affected paid orders.
     */
    private final List<Integer> memberPinkIds;

    public LitemallGroupExpiredEvent(LitemallCombinationPinkId groupPinkId,
                                     LitemallCombinationId combinationId, int memberCount,
                                     List<Integer> memberPinkIds) {
        super("GROUP_EXPIRED");
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
