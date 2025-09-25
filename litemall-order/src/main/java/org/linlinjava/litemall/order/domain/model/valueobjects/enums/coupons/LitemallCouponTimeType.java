package org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons;

import org.linlinjava.litemall.db.util.CouponConstant;

public enum LitemallCouponTimeType {

    TIME_TYPE_DAYS(CouponConstant.TIME_TYPE_DAYS),
    TIME_TYPE_TIME(CouponConstant.TIME_TYPE_TIME);

    private final Short value;
    LitemallCouponTimeType(Short value) {
        this.value = value;
    }
    public Short getValue() {
        return value;
    }
}
