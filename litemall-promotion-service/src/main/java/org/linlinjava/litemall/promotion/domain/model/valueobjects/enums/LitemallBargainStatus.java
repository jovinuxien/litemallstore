package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

public enum LitemallBargainStatus {

    ACTIVE(1, "Active") {
        @Override
        public boolean canTransitionTo(LitemallBargainStatus newStatus) {
            return newStatus == INACTIVE;
        }
    },
    INACTIVE(0, "Inactive") {
        @Override
        public boolean canTransitionTo(LitemallBargainStatus newStatus) {
            return newStatus == ACTIVE;
        }
    };

    private final int code;
    private final String displayName;

    LitemallBargainStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract boolean canTransitionTo(LitemallBargainStatus newStatus);

    public static LitemallBargainStatus fromCode(int code) {
        for (LitemallBargainStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallBargainStatus code: " + code);
    }
}
