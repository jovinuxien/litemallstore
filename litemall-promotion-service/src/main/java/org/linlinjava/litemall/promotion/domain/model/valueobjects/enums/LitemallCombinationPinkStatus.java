package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

/**
 * Lifecycle of a group-buy group ("pink"): pending until the required
 * headcount joins in time, then success; expired groups fail.
 */
public enum LitemallCombinationPinkStatus {

    PENDING(0, "Pending"),
    SUCCESS(1, "Success"),
    FAILED(2, "Failed");

    private final int code;
    private final String displayName;

    LitemallCombinationPinkStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallCombinationPinkStatus fromCode(int code) {
        for (LitemallCombinationPinkStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallCombinationPinkStatus code: " + code);
    }
}
