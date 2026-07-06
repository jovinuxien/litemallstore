package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallExtractStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallExtractId;

import java.time.LocalDateTime;

/**
 * Extract (withdrawal) aggregate. Maps to the litemall_user_extract table.
 */
@Getter
@Setter
public class LitemallExtractAggregate {

    private LitemallExtractId extractId;
    private LitemallUserId userId;
    private String realName;
    private String extractType;
    private String bankCode;
    private String bankAddress;
    private LitemallMoney extractAmount;
    private LitemallMoney balanceAfter;
    private LitemallExtractStatus status;
    private String failMsg;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public LitemallExtractAggregate() {
    }

    public LitemallExtractAggregate(LitemallUserId userId, String realName, String extractType,
                                     String bankCode, String bankAddress, LitemallMoney extractAmount,
                                     LitemallMoney balanceAfter) {
        this.userId = userId;
        this.realName = realName;
        this.extractType = extractType;
        this.bankCode = bankCode;
        this.bankAddress = bankAddress;
        this.extractAmount = extractAmount;
        this.balanceAfter = balanceAfter;
        this.status = LitemallExtractStatus.PENDING;
        this.addTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }
}
