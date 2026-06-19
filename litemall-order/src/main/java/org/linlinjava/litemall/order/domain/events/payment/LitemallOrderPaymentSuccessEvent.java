package org.linlinjava.litemall.order.domain.events.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

@Getter
public class LitemallOrderPaymentSuccessEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final LitemallMoney paidAmount;
    private final LocalDateTime paidTime;

    public LitemallOrderPaymentSuccessEvent(LitemallOrderId orderId, LitemallMoney paidAmount, LocalDateTime paidTime) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.paidAmount = paidAmount;
        this.paidTime = paidTime;
    }
}
