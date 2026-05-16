package org.linlinjava.litemall.wallet.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.enums.LitemallRechargeType;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallRechargeId;

import java.time.LocalDateTime;

/**
 * Recharge aggregate. Maps to the litemall_user_recharge table.
 */
@Getter
@Setter
public class LitemallRechargeAggregate {

    private LitemallRechargeId rechargeId;
    private LitemallUserId userId;
    private String orderId;
    private LitemallMoney price;
    private LitemallMoney givePrice;
    private LitemallRechargeType rechargeType;
    private Boolean paid;
    private LocalDateTime payTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public LitemallRechargeAggregate() {
    }

    public LitemallRechargeAggregate(LitemallUserId userId, String orderId, LitemallMoney price,
                                      LitemallMoney givePrice, LitemallRechargeType rechargeType) {
        this.userId = userId;
        this.orderId = orderId;
        this.price = price;
        this.givePrice = givePrice;
        this.rechargeType = rechargeType;
        this.paid = false;
        this.addTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public void markAsPaid() {
        this.paid = true;
        this.payTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }
}
