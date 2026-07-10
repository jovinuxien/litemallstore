package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/**
 * Customer command to receive (claim) a coupon into their wallet. {@code code}
 * is only required for redemption-code coupons.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallReceiveCouponCommand {

    private LitemallUserId userId;
    private LitemallCouponId couponId;
    private String code;
}
