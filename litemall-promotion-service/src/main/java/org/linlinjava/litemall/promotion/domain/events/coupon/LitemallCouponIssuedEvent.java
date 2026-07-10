package org.linlinjava.litemall.promotion.domain.events.coupon;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;

public class LitemallCouponIssuedEvent extends LitemallDomainEvent {

    private final LitemallCouponId couponId;
    private final String name;

    public LitemallCouponIssuedEvent(LitemallCouponId couponId, String name) {
        super("COUPON_ISSUED");
        this.couponId = couponId;
        this.name = name;
    }

    public LitemallCouponId getCouponId() { return couponId; }
    public String getName() { return name; }
}
