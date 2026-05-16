package org.linlinjava.litemall.promotion.domain.model.valueobjects.enums;

public enum LitemallSeckillStatus {

    ACTIVE(1, "Active") {
        @Override
        public boolean canTransitionTo(LitemallSeckillStatus newStatus) {
            return newStatus == INACTIVE;
        }
    },
    INACTIVE(0, "Inactive") {
        @Override
        public boolean canTransitionTo(LitemallSeckillStatus newStatus) {
            return newStatus == ACTIVE;
        }
    };

    private final int code;
    private final String displayName;

    LitemallSeckillStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract boolean canTransitionTo(LitemallSeckillStatus newStatus);

    public static LitemallSeckillStatus fromCode(int code) {
        for (LitemallSeckillStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown LitemallSeckillStatus code: " + code);
    }
}
