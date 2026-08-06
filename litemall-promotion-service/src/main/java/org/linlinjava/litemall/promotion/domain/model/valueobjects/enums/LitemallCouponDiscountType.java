package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * How a coupon's {@code discount} column is interpreted (Wave 18, V51).
 * FLAT keeps the pre-V51 semantics (discount = absolute amount off);
 * PERCENT makes discount the percentage rate (validated 1-90), optionally
 * bounded by {@code discount_cap}.
 */
public enum LitemallCouponDiscountType {

    FLAT(0, "Flat amount"),
    PERCENT(1, "Percent");

    private final int code;
    private final String displayName;

    LitemallCouponDiscountType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCouponDiscountType fromCode(int code) {
        for (LitemallCouponDiscountType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCouponDiscountType code: " + code);
    }
}
