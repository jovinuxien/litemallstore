package org.linlinjava.litemall.promotion.domain.events.combination;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallGroupMemberJoinedEvent extends LitemallDomainEvent {

    private final LitemallCombinationPinkId groupPinkId;
    private final LitemallCombinationId combinationId;
    private final LitemallUserId memberId;
    private final int memberCount;

    public LitemallGroupMemberJoinedEvent(LitemallCombinationPinkId groupPinkId,
                                          LitemallCombinationId combinationId,
                                          LitemallUserId memberId, int memberCount) {
        super("GROUP_MEMBER_JOINED");
        this.groupPinkId = groupPinkId;
        this.combinationId = combinationId;
        this.memberId = memberId;
        this.memberCount = memberCount;
    }

    public LitemallCombinationPinkId getGroupPinkId() { return groupPinkId; }
    public LitemallCombinationId getCombinationId() { return combinationId; }
    public LitemallUserId getMemberId() { return memberId; }
    public int getMemberCount() { return memberCount; }
}
