package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Lifecycle of a coupon definition. Mirrors litemall-db
 * {@code CouponConstant.STATUS_*}.
 */
public enum LitemallCouponStatus {

    NORMAL(0, "Normal"),
    EXPIRED(1, "Expired"),
    OUT(2, "Used up");

    private final int code;
    private final String displayName;

    LitemallCouponStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCouponStatus fromCode(int code) {
        for (LitemallCouponStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCouponStatus code: " + code);
    }
}
