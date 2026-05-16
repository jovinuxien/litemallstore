package org.linlinjava.litemall.wallet.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.enums.LitemallBillDirection;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallBillId;

import java.time.LocalDateTime;

/**
 * Bill aggregate. Maps to the litemall_user_bill table.
 */
@Getter
@Setter
public class LitemallBillAggregate {

    private LitemallBillId billId;
    private LitemallUserId userId;
    private String linkId;
    private LitemallBillDirection direction;
    private String title;
    private String category;
    private String type;
    private LitemallMoney amount;
    private LitemallMoney balanceAfter;
    private String mark;
    private Byte status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public LitemallBillAggregate() {
    }

    public LitemallBillAggregate(LitemallUserId userId, String linkId, LitemallBillDirection direction,
                                  String title, String category, String type,
                                  LitemallMoney amount, LitemallMoney balanceAfter, String mark) {
        this.userId = userId;
        this.linkId = linkId;
        this.direction = direction;
        this.title = title;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.mark = mark;
        this.status = 1;
        this.addTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }
}
