package org.linlinjava.litemall.order.domain.model.events.payment;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

@Getter
public class LitemallOrderPaymentSuccessEvent extends LitemallDomainEvent {

    private final LitemallOrderId orderId;
    private final LitemallMoney paidAmount;
    private final LocalDateTime paidTime;

    public LitemallOrderPaymentSuccessEvent(LitemallOrderId orderId, LitemallMoney paidAmount, LocalDateTime paidTime) {
        super("ORDER_PAYMENT_SUCCESS");
        this.orderId = orderId;
        this.paidAmount = paidAmount;
        this.paidTime = paidTime;
    }
}
