package org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons;

import org.linlinjava.litemall.db.util.CouponConstant;

public enum LitemallCouponType {
    COMMON(CouponConstant.TYPE_COMMON),
    REGISTER(CouponConstant.TYPE_REGISTER),
    CODE(CouponConstant.TYPE_CODE);

    private final Short value;

    LitemallCouponType(Short value) {
        this.value = value;
    }
    public Short getValue() {
        return value;
    }
}
