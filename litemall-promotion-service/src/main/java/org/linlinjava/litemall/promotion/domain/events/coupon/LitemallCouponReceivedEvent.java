package org.linlinjava.litemall.promotion.domain.events.coupon;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallCouponReceivedEvent extends LitemallDomainEvent {

    private final LitemallUserCouponId userCouponId;
    private final LitemallUserId userId;
    private final LitemallCouponId couponId;

    public LitemallCouponReceivedEvent(LitemallUserCouponId userCouponId, LitemallUserId userId,
                                       LitemallCouponId couponId) {
        super("COUPON_RECEIVED");
        this.userCouponId = userCouponId;
        this.userId = userId;
        this.couponId = couponId;
    }

    public LitemallUserCouponId getUserCouponId() { return userCouponId; }
    public LitemallUserId getUserId() { return userId; }
    public LitemallCouponId getCouponId() { return couponId; }
}
