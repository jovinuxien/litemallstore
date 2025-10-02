package org.linlinjava.litemall.order.application.internal.util;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

public class OrderActionRequest {

    // Getters
    @Getter
    private final LitemallOrderId orderId;
    @Getter
    private final LitemallUserId userId;
    @Getter
    private final String reason;
    //private final PaymentInfo paymentInfo;
    private final Object paymentInfo;

    // Constructors for different scenarios
    public OrderActionRequest(LitemallOrderId orderId, LitemallUserId userId, String reason) {
        this.orderId = orderId;
        this.userId = userId;
        this.reason = reason;
        this.paymentInfo = null;
    }

    public OrderActionRequest(LitemallOrderId orderId, LitemallUserId userId, Object paymentInfo) {
        this.orderId = orderId;
        this.userId = userId;
        this.paymentInfo = paymentInfo;
        this.reason = null;
    }

    public Object getPaymentInfo() { return paymentInfo; }
}
