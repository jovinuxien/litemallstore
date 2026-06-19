package org.linlinjava.litemall.order.domain.events.wallet;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallRechargeId;

@Getter
public class LitemallRechargeCompletedEvent extends LitemallDomainEvent {

    private final LitemallRechargeId rechargeId;
    private final LitemallUserId userId;
    private final LitemallMoney amount;

    public LitemallRechargeCompletedEvent(LitemallRechargeId rechargeId, LitemallUserId userId,
                                           LitemallMoney amount) {
        super("RECHARGE_COMPLETED");
        this.rechargeId = rechargeId;
        this.userId = userId;
        this.amount = amount;
    }
}
