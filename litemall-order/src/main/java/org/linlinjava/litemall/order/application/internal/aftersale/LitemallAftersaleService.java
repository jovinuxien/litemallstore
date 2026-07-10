package org.linlinjava.litemall.order.application.internal.aftersale;

import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAftersaleRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Customer-side aftersale/RMA operations for LOCAL orders, mirroring the CJ dispute
 * vertical's shape ({@code CjDisputeService}) with a locally-owned lifecycle. Every
 * read and write is scoped to the gateway-injected caller — a non-owner sees "not
 * found", never someone else's application. Admin decisions (approve → tender-parity
 * refund, reject) live on the orchestrator, which owns refund settlement.
 *
 * <p>Aftersale hops are recorded on the order's status-history timeline as
 * same-status entries ({@code aftersale_*}), so {@code GET /srv/order/{id}/timeline}
 * shows the full trail without a second timeline store.
 */
@Service
public class LitemallAftersaleService {

    private final LitemallAftersaleRepository aftersaleRepository;
    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;

    public LitemallAftersaleService(LitemallAftersaleRepository aftersaleRepository,
                                    LitemallOrderRepository orderRepository,
                                    LitemallOrderStatusHistoryRepository statusHistoryRepository) {
        this.aftersaleRepository = aftersaleRepository;
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    /** The apply form payload; the aggregate enforces every business guard. */
    public record ApplyCommand(Short type, String reason, BigDecimal amount,
                               String[] pictures, String comment) {
    }

    /**
     * Open an application on an order the caller owns. One open application per
     * order at a time; the amount defaults to (and is capped at) what was paid.
     * The order row is flagged {@code after_sale_status = applied} and the hop
     * lands on the timeline.
     */
    @Transactional
    public LitemallAftersaleAggregate apply(LitemallUserId userId, LitemallOrderId orderId,
                                            ApplyCommand command) {
        LitemallOrderAggregate order = ownedOrder(userId, orderId);
        aftersaleRepository.findOpenByOrder(orderId).ifPresent(open -> {
            throw new LitemallAftersaleException(
                    "An aftersale application (" + open.getAftersaleSn() + ") is already in progress for this order");
        });
        LitemallAftersaleAggregate aftersale = LitemallAftersaleAggregate.apply(
                order, userId, command.type(), command.reason(), command.amount(),
                command.pictures(), command.comment());
        aftersale.setAftersaleSn(order.getOrderSn() + "-A" + (aftersaleRepository.countByOrder(orderId) + 1));
        aftersaleRepository.add(aftersale);
        orderRepository.updateAfterSaleStatus(orderId, LitemallAfterSaleStatus.STATUS_REQUEST.getCode());
        recordTimelineHop(order, "aftersale_request",
                "Aftersale " + aftersale.getAftersaleSn() + " requested: " + aftersale.getReason(), "user");
        return aftersale;
    }

    /** The order's applications, newest first — owner only. */
    public List<LitemallAftersaleAggregate> listForOrder(LitemallUserId userId, LitemallOrderId orderId) {
        ownedOrder(userId, orderId);
        return aftersaleRepository.findByOrder(orderId);
    }

    /** One application — owner only, and it must belong to the path order. */
    public LitemallAftersaleAggregate detail(LitemallUserId userId, LitemallOrderId orderId,
                                             Integer aftersaleId) {
        ownedOrder(userId, orderId);
        LitemallAftersaleAggregate aftersale = aftersaleRepository.findById(aftersaleId)
                .orElseThrow(() -> new LitemallAftersaleException("Aftersale not found"));
        if (!aftersale.getOrderId().getId().equals(orderId.getId())
                || !aftersale.getUserId().getId().equals(userId.getId())) {
            throw new LitemallAftersaleException("Aftersale not found");
        }
        return aftersale;
    }

    /** Withdraw a still-undecided application; resets the order's aftersale flag. */
    @Transactional
    public void cancel(LitemallUserId userId, LitemallOrderId orderId, Integer aftersaleId) {
        LitemallOrderAggregate order = ownedOrder(userId, orderId);
        LitemallAftersaleAggregate aftersale = detail(userId, orderId, aftersaleId);
        aftersale.cancel(userId);
        aftersaleRepository.update(aftersale);
        orderRepository.updateAfterSaleStatus(orderId, LitemallAfterSaleStatus.STATUS_CANCEL.getCode());
        recordTimelineHop(order, "aftersale_cancel",
                "Aftersale " + aftersale.getAftersaleSn() + " cancelled by the customer", "user");
    }

    /** Admin queue (paged, optional status/order/user filters). */
    public List<LitemallAftersaleAggregate> adminList(Short status, Integer orderId, Integer userId,
                                                      int page, int limit) {
        return aftersaleRepository.adminQuery(status, orderId, userId, page, limit);
    }

    public long adminCount(Short status, Integer orderId, Integer userId) {
        return aftersaleRepository.adminCount(status, orderId, userId);
    }

    /** Owner-scoped order load: a non-owner (or unknown id) reads as not-found. */
    private LitemallOrderAggregate ownedOrder(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getUserId() == null
                || !order.getUserId().getId().equals(userId.getId())) {
            throw new LitemallAftersaleException("Order not found");
        }
        return order;
    }

    /**
     * A same-status timeline entry: aftersale hops that don't move the ORDER status
     * still belong on the order's single timeline.
     */
    private void recordTimelineHop(LitemallOrderAggregate order, String changeType,
                                   String message, String operator) {
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                order.getOrderId(), order.getOrderStatus(), order.getOrderStatus(),
                changeType, message, operator, LocalDateTime.now()));
    }
}
