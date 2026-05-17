package org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

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
