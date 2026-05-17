package org.linlinjava.litemall.wallet.domain.model.valueobjects.enums;

public enum LitemallExtractStatus {

    PENDING(0, "Pending"),
    PROCESSING(1, "Processing"),
    COMPLETED(2, "Completed"),
    REJECTED(-1, "Rejected");

    private final int code;
    private final String displayName;

    LitemallExtractStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallExtractStatus fromCode(int code) {
        for (LitemallExtractStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown extract status code: " + code);
    }
}
