package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request body for redeeming a held coupon at checkout. The userCouponId and
 * userId are taken from the path / authenticated context, not the body.
 */
@Getter
@Setter
public class CouponRedeemRequest {

    private Integer orderId;
    private BigDecimal orderSubtotal;
}
