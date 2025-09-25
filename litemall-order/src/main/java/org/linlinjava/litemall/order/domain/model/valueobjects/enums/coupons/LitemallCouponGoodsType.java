package org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons;

import org.linlinjava.litemall.db.util.CouponConstant;

public enum LitemallCouponGoodsType {

    GOODS_TYPE_ALL(CouponConstant.GOODS_TYPE_ALL),
    GOODS_TYPE_CATEGORY(CouponConstant.GOODS_TYPE_CATEGORY),
    GOODS_TYPE_ARRAY(CouponConstant.GOODS_TYPE_ARRAY);

    private final Short value;

    LitemallCouponGoodsType(Short value) {
        this.value = value;
    }
    public Short getValue() {
        return value;
    }
}
