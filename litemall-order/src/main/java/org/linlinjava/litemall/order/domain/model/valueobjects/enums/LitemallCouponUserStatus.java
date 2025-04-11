package org.linlinjava.litemall.order.domain.model.valueobjects.enums;

import org.linlinjava.litemall.db.util.CouponUserConstant;

public enum LitemallCouponUserStatus {

    USABLE(CouponUserConstant.STATUS_USABLE),
    USED(CouponUserConstant.STATUS_USED),
    EXPIRED(CouponUserConstant.STATUS_EXPIRED);

    private final Short value;

     LitemallCouponUserStatus(Short value) {
        this.value = value;
    }

    public Short getValue() {
        return value;
    }
}
