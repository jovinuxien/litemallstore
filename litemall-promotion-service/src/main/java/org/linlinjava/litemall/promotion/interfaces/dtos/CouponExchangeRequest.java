package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

/** Request body for exchanging a redemption code for its coupon. */
@Getter
@Setter
public class CouponExchangeRequest {

    private String code;
}
