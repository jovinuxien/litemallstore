package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Scope of goods a coupon applies to. Mirrors litemall-db
 * {@code CouponConstant.GOODS_TYPE_*}.
 */
public enum LitemallCouponGoodsType {

    ALL(0, "All goods"),
    CATEGORY(1, "Category"),
    ARRAY(2, "Specific goods");

    private final int code;
    private final String displayName;

    LitemallCouponGoodsType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCouponGoodsType fromCode(int code) {
        for (LitemallCouponGoodsType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCouponGoodsType code: " + code);
    }
}
