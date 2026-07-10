package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/**
 * Release a redeemed coupon after the order that consumed it failed
 * (payment/stock failure at checkout). Idempotent: releasing an
 * already-usable coupon succeeds without effect.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallReleaseCouponCommand {

    private LitemallUserCouponId userCouponId;
    private LitemallUserId userId;
    private Integer orderId;
}
