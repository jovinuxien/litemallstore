package org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion;

import java.math.BigDecimal;

/**
 * A coupon holding the promotion service reports as usable for a given checkout
 * (owned by the user, USABLE, in window, threshold met, goods scope matched).
 * {@code userCouponId} is the handle the redeem step consumes.
 */
public class UsableCoupon {

    private final Integer userCouponId;
    private final Integer couponId;
    private final String name;
    private final BigDecimal discount;
    private final BigDecimal min;

    public UsableCoupon(Integer userCouponId, Integer couponId, String name,
                        BigDecimal discount, BigDecimal min) {
        this.userCouponId = userCouponId;
        this.couponId = couponId;
        this.name = name;
        this.discount = discount;
        this.min = min;
    }

    public Integer getUserCouponId() {
        return userCouponId;
    }

    public Integer getCouponId() {
        return couponId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getMin() {
        return min;
    }
}
