package org.linlinjava.litemall.order.domain.model.valueobjects.enums;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import java.util.Arrays;

public enum LitemallOrderStatus {

    CREATED(101, "UNPAID"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            return newStatus == PAID || newStatus == CANCELED || newStatus == SYSTEM_CANCELED;
        }
    },
    PAID(201, "PAID"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            // Once paid, the order can be fulfilled (SHIPPED) or unwound via the
            // refund flow. A paid order is never hard-CANCELED — that path is a refund.
            return newStatus == SHIPPED || newStatus == REFUND_REQUEST;
        }
    },
    SHIPPED(301, "SHIPPED"){
        @Override
        public boolean canTransitionTo(LitemallOrderStatus newStatus) {
            // Customer confirms receipt (DELIVERED), the system auto-confirms after the
            // grace window (AUTO_DELIVERED), or the customer opens a return (REFUND_REQUEST).
            return newStatus == DELIVERED || newStatus == AUTO_DELIVERED || newStatus == REFUND_REQUEST;
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
