package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

@Getter
public class LitemallCouponId {

    private final Integer id;

    public LitemallCouponId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Coupon ID must be a positive integer.");
        }
        this.id = id;
    }
}
