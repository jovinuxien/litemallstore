package org.linlinjava.litemall.order.domain.model.util;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;

import java.util.*;

public class LitemallOrderStatusQuery {

    // Use domain types consistently
    public static List<LitemallOrderStatus> getStatusesForShowType(int showType) {
        switch (showType) {
            case 1: return List.of(LitemallOrderStatus.CREATED);
            case 2: return List.of(LitemallOrderStatus.PAID);
            case 3: return List.of(LitemallOrderStatus.SHIPPED);
            case 4: return List.of(LitemallOrderStatus.DELIVERED);
            default: return Collections.emptyList();
        }
    }

    // Improved: Work with domain objects, not primitives
    public static boolean isCreateStatus(LitemallOrderAggregate order) {
        return order.getOrderStatus() == LitemallOrderStatus.CREATED;
    }

    public static boolean hasPayed(LitemallOrderAggregate order) {
        return order.getOrderStatus() != LitemallOrderStatus.CREATED
                && order.getOrderStatus() != LitemallOrderStatus.CANCELED
                && order.getOrderStatus() != LitemallOrderStatus.SYSTEM_CANCELED;
    }

    // Additional useful queries
    public static boolean canBeCanceled(LitemallOrderAggregate order) {
        return order.getOrderStatus() == LitemallOrderStatus.CREATED
                || order.getOrderStatus() == LitemallOrderStatus.PAID;
    }

    public static boolean isFinalStatus(LitemallOrderAggregate order) {
        return order.getOrderStatus() == LitemallOrderStatus.DELIVERED
                || order.getOrderStatus() == LitemallOrderStatus.CANCELED
                || order.getOrderStatus() == LitemallOrderStatus.REFUNDED;
    }

    private static Short intToShort(int status) {
        return (short) status;
    }

    public static boolean isActionAllowed(LitemallOrderAggregate order, LitemallOrderOrchestratorService.OrderAction action) {
        LitemallOrderHandleOption options = LitemallOrderHandleOption.forStatus(order.getOrderStatus());

        return switch (action) {
            case CANCEL -> options.isCancel();
            case PAY -> options.isPay();
            case CONFIRM_SHIPPING, CONFIRM_DELIVERY -> options.isConfirm();
            case REQUEST_REFUND -> options.isRefund();
            case ADD_COMMENT -> options.isComment();
            case REBUY -> options.isRebuy();
            case REQUEST_AFTERSALE -> options.isAftersale();
            default -> false;
        };
    }

    /**
     * Single source of truth: the legal transition graph lives on {@link LitemallOrderStatus#canTransitionTo}.
     * This helper delegates so callers cannot drift from the aggregate's own guards.
     */
    public static boolean isValidTransition(LitemallOrderStatus from, LitemallOrderStatus to) {
        return from != null && to != null && from.canTransitionTo(to);
    }
}
