package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/**
 * Customer command to exchange a redemption code for its coupon. The coupon is
 * resolved from the code alone (crmeb exchange-by-code).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallExchangeCouponCommand {

    private LitemallUserId userId;
    private String code;
}
