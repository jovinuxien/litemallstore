package org.linlinjava.litemall.order.domain.model.valueobjects.enums;

import org.linlinjava.litemall.db.util.CouponConstant;

public enum LitemallCouponStatus {

    NORMAL(CouponConstant.STATUS_NORMAL),
    EXPIRED(CouponConstant.STATUS_EXPIRED);

    private final Short value;

    LitemallCouponStatus(Short value) {
        this.value = value;
    }
    public Short getValue() {
        return value;
    }
}
