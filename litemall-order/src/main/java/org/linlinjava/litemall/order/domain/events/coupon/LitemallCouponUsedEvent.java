package org.linlinjava.litemall.order.domain.model.events.coupon;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

public class LitemallCouponUsedEvent extends LitemallDomainEvent {

    private final LitemallCouponId couponId;
    private final LitemallOrderId orderId;
    private final Integer userId;
    private final LitemallMoney discountAmount;

    public LitemallCouponUsedEvent(LitemallCouponId couponId, LitemallOrderId orderId,
                           Integer userId, LitemallMoney discountAmount) {
        super("COUPON_USED");
        this.couponId = couponId;
        this.orderId = orderId;
        this.userId = userId;
        this.discountAmount = discountAmount;
    }
}
