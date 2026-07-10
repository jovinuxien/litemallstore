package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

/**
 * Request body for releasing a redeemed coupon after its order failed. The
 * orderId must be the order that consumed the coupon (replay-safe guard).
 */
@Getter
@Setter
public class CouponReleaseRequest {

    private Integer orderId;
}
