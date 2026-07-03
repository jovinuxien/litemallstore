package org.linlinjava.litemall.order.domain.model.repositories;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LitemallOrderRepository {

    Optional<LitemallOrderAggregate> findById(LitemallOrderId orderId);

    void addOrder(LitemallOrderAggregate order);

    int count(LitemallUserId userId);

    LitemallOrderAggregate findByIdAndUserId(LitemallUserId userId, LitemallOrderId orderId);

    int countByOrderSn(LitemallUserId userId, String orderSn);

    List<LitemallOrderAggregate> queryByOrderStatus(LitemallUserId userId, List<Short> orderStatus, int page, int limit, String sort, String order);

    /** Total orders for a user filtered by the same (optional) status set — the {@code total} for the paged list. */
    int countByOrderStatus(LitemallUserId userId, List<Short> orderStatus);

    /** Admin: page across ALL users' orders (not user-scoped). sortColumn must be a vetted DB column. */
    List<LitemallOrderAggregate> adminQuery(String orderSn, List<Short> orderStatus, int page, int limit, String sortColumn, String order);

    /** Admin: total order count matching the same filters as {@link #adminQuery}. */
    long adminCount(String orderSn, List<Short> orderStatus);

    void deleteByOrderId(LitemallOrderId orderId);

    public String generateOrderSn(LitemallUserId userId);

    int count();

    List<LitemallOrderAggregate> queryUnPaid(int minutes);

    List<LitemallOrderAggregate> queryUnconfirm(int days);

    Map<Object, Object> orderInfo(LitemallUserId userId);

    List<LitemallOrderAggregate> queryComment(int days);

    public int updateSelective(LitemallOrderAggregate orderAggregate);

    /**
     * Conditionally transition an order from CREATED to PAID. The update only
     * matches a row still in CREATED, so a retried or concurrent payment (which
     * already flipped the row) affects 0 rows. Returns the number of rows
     * updated (1 on success, 0 if the order was no longer in CREATED).
     *
     * @param payId tender record persisted to {@code pay_id} — {@code "WALLET"} or
     *              {@code "<METHOD>:<pspReference>"}; refund routing reads it back
     *              (see the orchestrator's refund-to-tender settlement). May be null.
     */
    int markPaidIfCreated(LitemallOrderId orderId, String payId);

    /**
     * Guarded status transitions. Each mirrors {@link #markPaidIfCreated}: the UPDATE
     * only matches a row in the expected source status, so concurrent/duplicate
     * transitions affect 0 rows (the caller treats that as a clean conflict). They
     * build a bare {@code LitemallOrder} patch touching only the relevant columns, so
     * they never go through the full aggregate→row conversion.
     */
    int markCanceledIfCreated(LitemallOrderId orderId);

    int markSystemCanceledIfCreated(LitemallOrderId orderId);

    int markShippedIfPaid(LitemallOrderId orderId, String shipChannel, String shipSn, java.time.LocalDateTime shipTime);

    int markDeliveredIfShipped(LitemallOrderId orderId, java.time.LocalDateTime confirmTime);

    int markAutoDeliveredIfShipped(LitemallOrderId orderId, java.time.LocalDateTime confirmTime);

    /** PAID or SHIPPED → REFUND_REQUEST. */
    int markRefundRequestedIfPayable(LitemallOrderId orderId, String refundContent);

    /** REFUND_REQUEST → REFUNDED. */
    int markRefundedIfRequested(LitemallOrderId orderId, java.math.BigDecimal refundAmount, java.time.LocalDateTime refundTime);

    void updateAfterSaleStatus(LitemallOrderId orderId, Short statuReject);
}
