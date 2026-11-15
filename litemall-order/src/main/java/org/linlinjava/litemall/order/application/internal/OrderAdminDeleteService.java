package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.application.internal.cj.CjPlacementMode;
import org.linlinjava.litemall.order.application.internal.cj.CjPlacementService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Admin order delete, backing {@code POST /srv/private/admin/order/{id}/delete}.
 *
 * <p>Always a SOFT delete (the logical-delete flag): the row, its lines and its timeline stay
 * in MySQL and the {@code order_sn} stays reserved. Nothing here moves money, restores stock
 * or talks to CJ — the endpoint removes an order from view, it does not undo it.
 *
 * <p>Two tiers:
 * <ul>
 *   <li><b>Finished orders</b> — exactly the statuses the customer may delete
 *       ({@link LitemallOrderHandleOption#isDelete()}): one rule, two callers.</li>
 *   <li><b>Paid orders that were never sent to CJ</b> (PAID / REFUND_REQUEST) — meant for test
 *       purchases. Because it hides an order whose money was taken, the caller must repeat
 *       the order number, and the order must be out of reach of the placement sweep: no CJ
 *       order id, and either unapproved under the manual gate or parked. An order the sweep
 *       could pick up is refused, so a delete can never race a CJ placement.</li>
 * </ul>
 */
@Service
public class OrderAdminDeleteService {

    private static final Logger log = LoggerFactory.getLogger(OrderAdminDeleteService.class);

    public static final String CHANGE_TYPE_ADMIN_DELETE = "admin_delete";

    public enum Status {
        DELETED,
        NOT_FOUND,
        ALREADY_DELETED,
        /** Unpaid, shipped, or otherwise mid-flight. */
        NOT_DELETABLE,
        /** Paid tier: the order number was not repeated (or does not match). */
        CONFIRM_REQUIRED,
        /** Paid tier: CJ already holds this order. */
        PLACED_AT_CJ,
        /** Paid tier: the placement sweep could send it to CJ at any moment. */
        QUEUED_FOR_CJ,
        /** The order changed between the check and the write. */
        LOST_RACE
    }

    public record Result(Status status, String message) {
    }

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final CjPlacementMode placementMode;

    public OrderAdminDeleteService(LitemallOrderRepository orderRepository,
                                   LitemallOrderStatusHistoryRepository statusHistoryRepository,
                                   CjPlacementMode placementMode) {
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.placementMode = placementMode;
    }

    /** True for the statuses the paid tier covers. */
    public static boolean isPaidTier(LitemallOrderStatus status) {
        return status == LitemallOrderStatus.PAID || status == LitemallOrderStatus.REFUND_REQUEST;
    }

    @Transactional
    public Result delete(Integer orderId, String confirmOrderSn, String adminUserId) {
        if (orderId == null) {
            return new Result(Status.NOT_FOUND, "order not found");
        }
        LitemallOrderId id = new LitemallOrderId(orderId);
        LitemallOrderAggregate order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return new Result(Status.NOT_FOUND, "order " + orderId + " not found");
        }
        if (Boolean.TRUE.equals(order.getDeleted())) {
            return new Result(Status.ALREADY_DELETED, "order " + orderId + " is already deleted");
        }
        LitemallOrderStatus status = order.getOrderStatus();
        boolean paidTier = false;
        if (status == null || !LitemallOrderHandleOption.forStatus(status).isDelete()) {
            if (status == null || !isPaidTier(status)) {
                return new Result(Status.NOT_DELETABLE,
                        "order " + orderId + " cannot be deleted in " + status + " status");
            }
            Result refusal = paidTierRefusal(order, confirmOrderSn);
            if (refusal != null) {
                return refusal;
            }
            paidTier = true;
        }

        int changed = orderRepository.softDeleteIfStatus(id, status);
        if (changed == 0) {
            return new Result(Status.LOST_RACE,
                    "order " + orderId + " changed while it was being deleted — reload and try again");
        }
        String by = StringUtils.hasText(adminUserId) ? adminUserId.trim() : "admin";
        // Same transaction as the flag: a delete without its "who" on the timeline rolls back.
        statusHistoryRepository.record(new LitemallOrderStatusChange(id, status, status,
                CHANGE_TYPE_ADMIN_DELETE,
                "Deleted by admin " + by + (paidTier ? " (paid, never sent to CJ; no money moved)" : ""),
                "admin:" + by, LocalDateTime.now()));
        log.info("Order {} (sn {}) soft-deleted by admin {} in status {}{}", orderId, order.getOrderSn(),
                by, status, paidTier ? " [paid tier]" : "");
        return new Result(Status.DELETED, "order " + order.getOrderSn() + " deleted");
    }

    private Result paidTierRefusal(LitemallOrderAggregate order, String confirmOrderSn) {
        int orderId = order.getOrderId().getId();
        if (StringUtils.hasText(order.getCjOrderId())) {
            return new Result(Status.PLACED_AT_CJ, "order " + orderId + " is already placed at CJ ("
                    + order.getCjOrderId() + ") and cannot be deleted while it is in progress");
        }
        if (order.isCjFulfilled() && order.getOrderStatus() == LitemallOrderStatus.PAID
                && !CjPlacementService.isParked(order.getCjOrderStatus())
                && (!placementMode.isManual() || order.getCjPlacementApprovedTime() != null)) {
            return new Result(Status.QUEUED_FOR_CJ, "order " + orderId
                    + " is queued for CJ placement and could be sent at any moment — it cannot be deleted");
        }
        if (!StringUtils.hasText(confirmOrderSn) || order.getOrderSn() == null
                || !order.getOrderSn().equals(confirmOrderSn.trim())) {
            return new Result(Status.CONFIRM_REQUIRED, "order " + orderId
                    + " is paid: repeat its order number to delete it (no refund is made)");
        }
        return null;
    }
}
