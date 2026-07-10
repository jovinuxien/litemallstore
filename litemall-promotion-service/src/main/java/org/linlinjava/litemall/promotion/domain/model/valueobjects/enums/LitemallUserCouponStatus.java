package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Lifecycle of a coupon held by a user (issuance → redemption). Mirrors
 * litemall-db {@code CouponUserConstant.STATUS_*}.
 */
public enum LitemallUserCouponStatus {

    USABLE(0, "Usable"),
    USED(1, "Used"),
    EXPIRED(2, "Expired"),
    OUT(3, "Withdrawn");

    private final int code;
    private final String displayName;

    LitemallUserCouponStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallUserCouponStatus fromCode(int code) {
        for (LitemallUserCouponStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallUserCouponStatus code: " + code);
    }
}
