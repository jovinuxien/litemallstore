package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * How a coupon definition may be acquired. Mirrors litemall-db
 * {@code CouponConstant.TYPE_*} (the on-disk codes are authoritative).
 */
public enum LitemallCouponType {

    COMMON(0, "Common"),
    REGISTER(1, "Registration"),
    CODE(2, "Redemption code");

    private final int code;
    private final String displayName;

    LitemallCouponType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCouponType fromCode(int code) {
        for (LitemallCouponType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCouponType code: " + code);
    }
}
