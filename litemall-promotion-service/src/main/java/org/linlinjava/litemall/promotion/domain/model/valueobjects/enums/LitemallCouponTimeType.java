package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * How a received coupon's validity window is computed. Mirrors litemall-db
 * {@code CouponConstant.TIME_TYPE_*}: DAYS = relative to receive time, TIME =
 * a fixed absolute window carried by the definition.
 */
public enum LitemallCouponTimeType {

    DAYS(0, "Relative days"),
    TIME(1, "Absolute window");

    private final int code;
    private final String displayName;

    LitemallCouponTimeType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCouponTimeType fromCode(int code) {
        for (LitemallCouponTimeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCouponTimeType code: " + code);
    }
}
