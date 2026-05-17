package org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.db.util.CouponConstant;

public enum LitemallCouponStatus {

    NORMAL(CouponConstant.STATUS_NORMAL),
    EXPIRED(CouponConstant.STATUS_EXPIRED),
    OUT(CouponConstant.STATUS_OUT);

    private final Short value;

    LitemallCouponStatus(Short value) {
        this.value = value;
    }
    public Short getValue() {
        return value;
    }
}
