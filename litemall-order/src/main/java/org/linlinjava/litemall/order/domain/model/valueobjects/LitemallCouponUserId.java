package org.linlinjava.litemall.order.domain.model.valueobjects;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallCouponUserId {

    private final Integer id;

    public LitemallCouponUserId(Integer id) {
        // 0 is the "no user-coupon" sentinel for a plain order (placeOrder normalizes
        // a missing selection to 0). Reject only null/negative.
        if (id == null || id < 0) {
            throw new IllegalArgumentException("Coupon user id must not be null or negative");
        }
        this.id = id;
    }
    public Integer getId() {
        return id;
    }
}
