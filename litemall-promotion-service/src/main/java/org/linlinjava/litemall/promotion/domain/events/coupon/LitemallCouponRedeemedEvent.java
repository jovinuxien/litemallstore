package org.linlinjava.litemall.promotion.domain.events.coupon;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallCouponRedeemedEvent extends LitemallDomainEvent {

    private final LitemallUserCouponId userCouponId;
    private final LitemallUserId userId;
    private final LitemallCouponId couponId;
    private final Integer orderId;
    private final LitemallMoney discount;

    public LitemallCouponRedeemedEvent(LitemallUserCouponId userCouponId, LitemallUserId userId,
                                       LitemallCouponId couponId, Integer orderId, LitemallMoney discount) {
        super("COUPON_REDEEMED");
        this.userCouponId = userCouponId;
        this.userId = userId;
        this.couponId = couponId;
        this.orderId = orderId;
        this.discount = discount;
    }

    public LitemallUserCouponId getUserCouponId() { return userCouponId; }
    public LitemallUserId getUserId() { return userId; }
    public LitemallCouponId getCouponId() { return couponId; }
    public Integer getOrderId() { return orderId; }
    public LitemallMoney getDiscount() { return discount; }
}
