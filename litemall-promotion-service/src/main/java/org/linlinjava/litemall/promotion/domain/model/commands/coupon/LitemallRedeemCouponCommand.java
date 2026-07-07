package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

import java.math.BigDecimal;

/**
 * Redeem-at-checkout command: apply a held coupon to an order. The order's
 * subtotal is validated against the coupon's spend threshold before the coupon
 * is marked used. {@code orderId} stamps the redemption.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallRedeemCouponCommand {

    private LitemallUserCouponId userCouponId;
    private LitemallUserId userId;
    private Integer orderId;
    private BigDecimal orderSubtotal;
}
