package org.linlinjava.litemall.order.domain.model.commands;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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

    // Immutable command with final fields => no default ctor for Jackson to use.
    // Bind the @RequestBody through this constructor so /srv/order/submit can
    // deserialize the JSON body (userId is overridden from the gateway header).
    @JsonCreator
    public LitemallPlaceOrderCommand(@JsonProperty("userId") Integer userId,
                                     @JsonProperty("cartId") Integer cartId,
                                     @JsonProperty("addressId") Integer addressId,
                                     @JsonProperty("couponId") Integer couponId,
                                     @JsonProperty("userCouponId") Integer userCouponId,
                                     @JsonProperty("message") String message,
                                     @JsonProperty("grouponRulesId") Integer grouponRulesId,
                                     @JsonProperty("grouponLinkId") Integer grouponLinkId) {
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
