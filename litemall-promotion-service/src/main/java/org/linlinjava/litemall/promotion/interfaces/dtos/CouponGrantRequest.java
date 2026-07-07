package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

/** Admin request body to push a coupon directly into a user's wallet. */
@Getter
@Setter
public class CouponGrantRequest {

    private Integer couponId;
    private Integer userId;
}
