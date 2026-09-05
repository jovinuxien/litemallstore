package org.linlinjava.litemall.order.domain.model.util;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;

import java.util.*;

public class LitemallOrderStatusQuery {

    // Improved: Work with domain objects, not primitives
    public static boolean isCreateStatus(LitemallOrderAggregate order) {
        return order.getOrderStatus() == LitemallOrderStatus.CREATED;
    }

    public static boolean hasPayed(LitemallOrderAggregate order) {
        return order.getOrderStatus() != LitemallOrderStatus.CREATED
                && order.getOrderStatus() != LitemallOrderStatus.CANCELED
                && order.getOrderStatus() != LitemallOrderStatus.SYSTEM_CANCELED;
    }

    /**
     * Whether the CUSTOMER may cancel: CREATED only — a paid order goes through refund. Until
     * 2026-09-05 this claimed CREATED||PAID, contradicting {@link LitemallOrderHandleOption}
     * (the dispatcher's real gate) and the enum. Delegates so the two cannot drift again.
     */
    public static boolean canBeCanceled(LitemallOrderAggregate order) {
        return isActionAllowed(order, LitemallOrderOrchestratorService.OrderAction.CANCEL);
    }

    /** Terminal for the lifecycle: nothing the customer or the system does moves it further on its own. */
    public static boolean isFinalStatus(LitemallOrderAggregate order) {
        return order.getOrderStatus() == LitemallOrderStatus.CANCELED
                || order.getOrderStatus() == LitemallOrderStatus.SYSTEM_CANCELED
                || order.getOrderStatus() == LitemallOrderStatus.REFUNDED;
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
