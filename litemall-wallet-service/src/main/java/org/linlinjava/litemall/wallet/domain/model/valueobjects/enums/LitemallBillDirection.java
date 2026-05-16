package org.linlinjava.litemall.wallet.domain.model.valueobjects.enums;

public enum LitemallBillDirection {

    CREDIT(1, "Income"),
    DEBIT(0, "Expense");

    private final int code;
    private final String displayName;

    LitemallBillDirection(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LitemallBillDirection fromCode(int code) {
        for (LitemallBillDirection direction : values()) {
            if (direction.code == code) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown bill direction code: " + code);
    }
}