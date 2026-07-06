package org.linlinjava.litemall.order.interfaces.dtos.order;

import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;

/**
 * Maps an {@link LitemallOrderStatus} to the human-readable label the customer
 * "My Orders" SPA renders ({@code orderStatusText}). Mirrors the litemall-vue
 * status wording; falls back to the enum display name for unmapped states.
 */
public final class OrderStatusText {

    private OrderStatusText() {
    }

    public static String of(LitemallOrderStatus status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case CREATED:
                return "Unpaid";
            case PAID:
                return "To be shipped";
            case SHIPPED:
                return "Shipped";
            case DELIVERED:
            case AUTO_DELIVERED:
                return "Completed";
            case CANCELED:
            case SYSTEM_CANCELED:
                return "Cancelled";
            case REFUND_REQUEST:
                return "Refund in progress";
            case REFUNDED:
                return "Refunded";
            default:
                return status.getDisplayName();
        }
    }
}
