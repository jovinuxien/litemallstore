package org.linlinjava.litemall.order.domain.events.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

@Getter
public class LitemallCouponUsedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallCouponId couponId;
    private final LitemallOrderId orderId;
    private final Integer userId;
    private final LitemallMoney discountAmount;

    public LitemallCouponUsedEvent(LitemallCouponId couponId, LitemallOrderId orderId,
                           Integer userId, LitemallMoney discountAmount) {
        super(SCHEMA_VERSION);
        this.couponId = couponId;
        this.orderId = orderId;
        this.userId = userId;
        this.discountAmount = discountAmount;
    }
}
