package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

public enum LitemallBargainUserStatus {

    ONGOING(0, "In Progress") {
        @Override
        public boolean canTransitionTo(LitemallBargainUserStatus newStatus) {
            return newStatus == SUCCESS || newStatus == FAILED;
        }
    },
    SUCCESS(1, "Success") {
        @Override
        public boolean canTransitionTo(LitemallBargainUserStatus newStatus) {
            return false;
        }
    },
    FAILED(2, "Failed") {
        @Override
        public boolean canTransitionTo(LitemallBargainUserStatus newStatus) {
            return false;
        }
    };

    private final int code;
    private final String displayName;

    LitemallBargainUserStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract boolean canTransitionTo(LitemallBargainUserStatus newStatus);

    public static LitemallBargainUserStatus fromCode(int code) {
        for (LitemallBargainUserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallBargainUserStatus code: " + code);
    }
}
