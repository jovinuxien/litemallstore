package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

@Getter
public class LitemallCouponId {

    private final Integer id;

    public LitemallCouponId(Integer id) {
        // litemall encodes "no coupon" as the sentinels 0 and -1 (placeOrder guards
        // real use with getId() != 0 && != -1). Reject only null/below-(-1) so a
        // plain order without a coupon doesn't blow up constructing this VO.
        if(id == null || id < -1) {
            throw new IllegalArgumentException("Coupon ID must not be null or below -1.");
        }
        this.id = id;
    }
}
