package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.events.groupon.LitemallGrouponCreatedEvent;
import org.linlinjava.litemall.order.domain.model.events.groupon.LitemallGrouponSucceededEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LitemallGrouponAggregate {

    private LitemallGrouponId grouponId;
    private LitemallOrderId orderId;
    private LitemallUserId userId;
    private LitemallGrouponRulesId grouponRulesId;
    private LitemallUserId creatorUserId;

    private LitemallGrouponStatus grouponStatus;
    private String shareUrl;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private LocalDateTime deleteTime;
    private LocalDateTime creatorUserTime;

    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();


    public static LitemallGrouponAggregate createNewGroupon(LitemallOrderId orderId,
                                    LitemallUserId userId,
                                   LitemallGrouponRulesId grouponRulesId) {
        LitemallGrouponAggregate grouponAggregate = new LitemallGrouponAggregate();

        grouponAggregate.setGrouponId(new LitemallGrouponId(0)); // For ths new groupon, set id to 0
        grouponAggregate.setOrderId(orderId);
        grouponAggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_NONE);
        grouponAggregate.setUserId(userId);
        grouponAggregate.setGrouponRulesId(grouponRulesId);
        grouponAggregate.creatorUserTime = LocalDateTime.now();

        grouponAggregate.domainEvents.add(new LitemallGrouponCreatedEvent(grouponAggregate));
        return grouponAggregate;
    }
    public static LitemallGrouponAggregate createJoin(LitemallOrderId orderId, LitemallUserId userId, LitemallGrouponRulesId grouponRulesId, LitemallGrouponAggregate baseGrouponAggregate){
        LitemallGrouponAggregate LitemallGrouponAggregate = new LitemallGrouponAggregate();
        LitemallGrouponAggregate.orderId = orderId;
        LitemallGrouponAggregate.userId = userId;
        LitemallGrouponAggregate.setGrouponRulesId(grouponRulesId);
        LitemallGrouponAggregate.creatorUserId = baseGrouponAggregate.getCreatorUserId();
        LitemallGrouponAggregate.grouponId = baseGrouponAggregate.getGrouponId();
        LitemallGrouponAggregate.shareUrl = baseGrouponAggregate.getShareUrl();
        LitemallGrouponAggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_NONE);

        LitemallGrouponAggregate.domainEvents.add(new LitemallGrouponCreatedEvent(LitemallGrouponAggregate));
        return LitemallGrouponAggregate;
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
