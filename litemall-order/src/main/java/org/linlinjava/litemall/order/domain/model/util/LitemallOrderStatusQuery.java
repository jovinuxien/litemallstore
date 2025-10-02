package org.linlinjava.litemall.order.domain.model.util;

import org.linlinjava.litemall.db.domain.LitemallOrder;
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

    public static boolean isValidTransition(LitemallOrderStatus from, LitemallOrderStatus to) {
        Map<LitemallOrderStatus, Set<LitemallOrderStatus>> validTransitions = Map.of(
                LitemallOrderStatus.CREATED, Set.of(LitemallOrderStatus.PAID, LitemallOrderStatus.CANCELED),
                LitemallOrderStatus.PAID, Set.of(LitemallOrderStatus.SHIPPED, LitemallOrderStatus.REFUNDED),
                LitemallOrderStatus.SHIPPED, Set.of(LitemallOrderStatus.DELIVERED, LitemallOrderStatus.REFUNDED)
                //LitemallOrderStatus.DELIVERED, Set.of(LitemallOrderStatus.COMPLETED, LitemallOrderStatus.REFUNDED)
        );
        return validTransitions.getOrDefault(from, Collections.emptySet()).contains(to);
    }
}
