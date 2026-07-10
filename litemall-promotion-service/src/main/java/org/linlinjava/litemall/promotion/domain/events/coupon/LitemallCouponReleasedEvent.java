package org.linlinjava.litemall.promotion.domain.events.coupon;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallCouponReleasedEvent extends LitemallDomainEvent {

    private final LitemallUserCouponId userCouponId;
    private final LitemallUserId userId;
    private final LitemallCouponId couponId;
    private final Integer orderId;

    public LitemallCouponReleasedEvent(LitemallUserCouponId userCouponId, LitemallUserId userId,
                                       LitemallCouponId couponId, Integer orderId) {
        super("COUPON_RELEASED");
        this.userCouponId = userCouponId;
        this.userId = userId;
        this.couponId = couponId;
        this.orderId = orderId;
    }

    public LitemallUserCouponId getUserCouponId() { return userCouponId; }
    public LitemallUserId getUserId() { return userId; }
    public LitemallCouponId getCouponId() { return couponId; }
    public Integer getOrderId() { return orderId; }
}
