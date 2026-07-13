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
    // OPTIONAL: ISO destination country picked at checkout. The address book stores
    // no country, but a CJ-fulfilled order needs one for createOrder at pay time.
    private final String countryCode;

    // In-store pickup (Wave 4, Task B). deliveryType: null/"express" (default) or
    // "pickup". Pickup requires storeId + pickupName + pickupMobile and makes
    // addressId optional (freight 0, address column stores "PICKUP: <store name>").
    private final String deliveryType;
    private final Integer storeId;
    private final String pickupName;
    private final String pickupMobile;

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
                                     @JsonProperty("grouponLinkId") Integer grouponLinkId,
                                     @JsonProperty("countryCode") String countryCode,
                                     @JsonProperty("deliveryType") String deliveryType,
                                     @JsonProperty("storeId") Integer storeId,
                                     @JsonProperty("pickupName") String pickupName,
                                     @JsonProperty("pickupMobile") String pickupMobile) {
        this.userId = userId;
        this.cartId = cartId;
        this.addressId = addressId;
        this.couponId = couponId;
        this.userCouponId = userCouponId;
        this.message = message;
        this.grouponRulesId = grouponRulesId;
        this.grouponLinkId = grouponLinkId;
        this.countryCode = countryCode;
        this.deliveryType = deliveryType;
        this.storeId = storeId;
        this.pickupName = pickupName;
        this.pickupMobile = pickupMobile;
    }

    /** Pre-Wave-4 shape (express delivery) — kept for existing callers and tests. */
    public LitemallPlaceOrderCommand(Integer userId, Integer cartId, Integer addressId,
                                     Integer couponId, Integer userCouponId, String message,
                                     Integer grouponRulesId, Integer grouponLinkId,
                                     String countryCode) {
        this(userId, cartId, addressId, couponId, userCouponId, message,
                grouponRulesId, grouponLinkId, countryCode, null, null, null, null);
    }

    /** True when the buyer chose in-store pickup. */
    public boolean isPickup() {
        return "pickup".equals(deliveryType);
    }
}
