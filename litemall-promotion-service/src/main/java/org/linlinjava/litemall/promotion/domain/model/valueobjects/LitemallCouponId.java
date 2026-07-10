package org.linlinjava.litemall.promotion.domain.model.valueobjects;

import lombok.Getter;

import java.util.Objects;

@Getter
public class LitemallCouponId {

    private final Integer id;

    public LitemallCouponId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Coupon ID must be a positive integer.");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallCouponId that = (LitemallCouponId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LitemallCouponId{id=" + id + "}";
    }
}
