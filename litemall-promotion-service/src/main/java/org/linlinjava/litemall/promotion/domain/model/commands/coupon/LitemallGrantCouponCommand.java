package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/**
 * Admin command to push a coupon directly into a specific user's wallet
 * (crmeb direct-send). Skips the customer receive window and redemption code
 * but still honours the total cap.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallGrantCouponCommand {

    private LitemallCouponId couponId;
    private LitemallUserId userId;
}
