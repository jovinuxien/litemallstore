package org.linlinjava.litemall.order.domain.model.commands;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;

@Data
public class LitemallPlaceOrderCommand {

    private final Integer userId;
    private final Integer cartId;
    private final Integer addressId;
    private final Integer couponId;
    private final Integer userCouponId;
    private final String message;
    private final Integer grouponRulesId;
    private final Integer grouponLinkId;

    public LitemallPlaceOrderCommand(Integer userId,
                                     Integer cartId,
                                     Integer addressId,
                                     Integer couponId,
                                     Integer userCouponId, String message, Integer grouponRulesId, Integer grouponLinkId) {
        this.userId = userId;
        this.cartId = cartId;
        this.addressId = addressId;
        this.couponId = couponId;
        this.userCouponId = userCouponId;
        this.message = message;
        this.grouponRulesId = grouponRulesId;
        this.grouponLinkId = grouponLinkId;
    }

}
