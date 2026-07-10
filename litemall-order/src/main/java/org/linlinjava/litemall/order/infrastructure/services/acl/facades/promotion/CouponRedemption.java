package org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion;

import java.math.BigDecimal;

/**
 * Result of a successful exactly-once coupon redemption at the promotion
 * service: the holding is now USED and stamped with this order. The discount is
 * promotion's authoritative amount — the caller must verify it matches what the
 * order was priced with before committing.
 */
public class CouponRedemption {

    private final Integer userCouponId;
    private final Integer couponId;
    private final Integer orderId;
    private final BigDecimal discount;

    public CouponRedemption(Integer userCouponId, Integer couponId, Integer orderId, BigDecimal discount) {
        this.userCouponId = userCouponId;
        this.couponId = couponId;
        this.orderId = orderId;
        this.discount = discount;
    }

    public Integer getUserCouponId() {
        return userCouponId;
    }

    public Integer getCouponId() {
        return couponId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public BigDecimal getDiscount() {
        return discount;
    }
}
