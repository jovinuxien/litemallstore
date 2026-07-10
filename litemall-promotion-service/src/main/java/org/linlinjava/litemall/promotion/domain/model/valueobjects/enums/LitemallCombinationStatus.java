package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Lifecycle of a combination (group-buy) campaign definition. DRAFT campaigns
 * are admin-only; ACTIVE campaigns are customer-visible and may seed group-buy
 * participation (owned by litemall-order).
 */
public enum LitemallCombinationStatus {

    DRAFT(0, "Draft"),
    ACTIVE(1, "Active"),
    EXPIRED(2, "Expired"),
    OFFLINE(3, "Offline");

    private final int code;
    private final String displayName;

    LitemallCombinationStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCombinationStatus fromCode(int code) {
        for (LitemallCombinationStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCombinationStatus code: " + code);
    }
}
