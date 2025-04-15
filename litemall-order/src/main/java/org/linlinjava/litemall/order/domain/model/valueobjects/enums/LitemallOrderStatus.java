package org.linlinjava.litemall.order.domain.model.valueobjects.enums;

import java.util.Arrays;

public enum LitemallOrderStatus {

    CREATED(101, "UNPAID"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return newStatus == PAID || newStatus == CANCELED ;
        }
    },
    PAID(201, "PAID"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return newStatus == SHIPPED || newStatus == CANCELED;
        }
    },
    SHIPPED(301, "SHIPPED"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return newStatus == DELIVERED ;
        }
    },
    DELIVERED(401, "DELIVERED"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return false; // Final state, no transition allowed.
        }
    },
    CANCELED(102, "CANCELLED"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return false; // Final state, no transition allowed.
        }
    },

    SYSTEM_CANCELED(103, "SYSTEM CANCELLED"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return false; // Final state, no transition allowed.
        }
    },
    REFUND_REQUEST(202, "REFUND IN PROGRESS"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return newStatus == REFUNDED;
        }
    },
    REFUNDED(203, "REFUNDED"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return false; // Final state, no transition allowed.
        }
    },

    AUTO_DELIVERED(402, "GOODS_RECEIVED(SYSTEM)"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return false; // Final state, no transition allowed.
        }
    };



    private final short code;
    private final String displayName;
    
    LitemallOrderStatus(int code, String displayName) {
        this.code = (short) code;
        this.displayName = displayName;
    }

    public short getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public abstract boolean canTransitionTo(LitemallOrderStatus newStatus);


    public static LitemallOrderStatus fromCode(short code) {
        return Arrays.stream(values())
                .filter(status -> status.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid order status code: " + code));
    }
}
