package org.linlinjava.litemall.order.domain.model.valueobjects;

public class LitemallCouponUserId {

    private final Integer id;

    public LitemallCouponUserId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Coupon user id must not be null or empty");
        }
        this.id = id;
    }
    public Integer getId() {
        return id;
    }
}
