package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallGrouponAggregate {

    private LitemallGrouponId grouponId;
    private LitemallOrderId orderId;
    private LitemallGrouponStatus grouponStatus;
    private LitemallUserId userId;
    private LitemallGrouponRulesId grouponRulesId;
    private String shareUrl;
    private LocalDateTime creatorUserTime;


    public LitemallGrouponAggregate createNewGroupon(LitemallGrouponId grouponId, LitemallOrderId orderId,
                                    LitemallUserId userId,
                                   LitemallGrouponRulesId grouponRulesId, String shareUrl) {
        LitemallGrouponAggregate aggregate = new LitemallGrouponAggregate();

        aggregate.setGrouponId(new LitemallGrouponId(0)); // For ths new groupon, set id to 0
        aggregate.setOrderId(orderId);
        aggregate.setGrouponStatus(LitemallGrouponStatus.STATUS_NONE);
        aggregate.setUserId(userId);
        aggregate.setGrouponRulesId(grouponRulesId);
        aggregate.setShareUrl(shareUrl);
        aggregate.creatorUserTime = LocalDateTime.now();
        return aggregate;
    }

    public void markAsOn() {
        this.grouponStatus = LitemallGrouponStatus.STATUS_ON;
        this.shareUrl = shareUrl;
    }



}
