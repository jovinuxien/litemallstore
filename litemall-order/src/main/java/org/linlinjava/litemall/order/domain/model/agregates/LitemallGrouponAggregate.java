package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.events.groupon.LitemallGrouponCreatedEvent;
import org.linlinjava.litemall.order.domain.model.events.groupon.LitemallGrouponSucceededEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.GrouponParticipant;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
public class LitemallGrouponAggregate {// Or the GrouponActivityAggregate

    private LitemallGrouponId grouponId;
    private LitemallOrderId orderId;
    //private LitemallUserId userId;
    private LitemallUserId creatorUserId;
    private LitemallGrouponRulesId grouponRulesId;
    private LocalDateTime creatorUserTime;


    private String shareUrl;
    private LitemallGrouponStatus grouponStatus;
    private Set<GrouponParticipant> participants;

    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;

    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();


    public LitemallGrouponAggregate(LitemallGrouponRulesId grouponRulesId, LitemallUserId userId, LitemallOrderId orderId) {
        this.grouponRulesId = grouponRulesId;
        this.creatorUserId = userId ;
        this.orderId = orderId;
        this.participants = new HashSet<>();
        this.grouponStatus = LitemallGrouponStatus.STATUS_ON;
        this.creatorUserTime = LocalDateTime.now();
        //this.addParticipant(new GrouponParticipant(userId, LocalDateTime.now()));
        this.addParticipant(userId);
    }


    // Domain methods
    public void addParticipant(LitemallUserId userId) {
        if (isFull()) {
            //throw new GrouponActivityFullException("Groupon activity is already full");
            throw new IllegalArgumentException("Groupon activity is already full");
        }
        if (hasParticipant(userId)) {
            //throw new DuplicateParticipantException("User already participated in this groupon");
            throw new IllegalArgumentException("User already participated in this groupon");
        }
        participants.add(new GrouponParticipant(userId, LocalDateTime.now()));

        if (isFull()) {
            this.grouponStatus = LitemallGrouponStatus.STATUS_SUCCEED;
            // Domain event: GrouponSuccessEvent
        }
    }

    public boolean isFull() {
        // This should be based on the rule's discountMember
        return participants.size() >= getRequiredMembers();
    }

    public boolean hasParticipant(LitemallUserId userId) {
        return participants.stream()
                .anyMatch(participants -> participants.getUserId().equals(userId));
    }

    private int getRequiredMembers() {
        // Would need to fetch from GrouponRule aggregate
        return 2; // Default for example
    }


    public void markAsOn() {
        this.grouponStatus = LitemallGrouponStatus.STATUS_ON;
        this.shareUrl = shareUrl;
    }

    public void markAsSucceeded(){
        this.setGrouponStatus(LitemallGrouponStatus.STATUS_SUCCEED);
        this.domainEvents.add(new LitemallGrouponSucceededEvent(this.grouponId));
    }


}
