package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
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

    /**
     * Orders carrying this order_sn — GLOBALLY and including soft-deleted rows. Was scoped
     * per-user + non-deleted, which contradicted both the DB's uk_order_order_sn (V43) and
     * CJ's merchant-order dedupe (Wave 7, Task E3).
     */
    int countByOrderSn(String orderSn);

    List<LitemallOrderAggregate> queryByOrderStatus(LitemallUserId userId, List<Short> orderStatus, int page, int limit, String sort, String order);

    /** Total orders for a user filtered by the same (optional) status set — the {@code total} for the paged list. */
    int countByOrderStatus(LitemallUserId userId, List<Short> orderStatus);

    /** Admin: page across ALL users' orders (not user-scoped). sortColumn must be a vetted DB column. */
    List<LitemallOrderAggregate> adminQuery(String orderSn, List<Short> orderStatus,
                                            java.time.LocalDateTime start, java.time.LocalDateTime end,
                                            int page, int limit, String sortColumn, String order);

    /** Admin: total order count matching the same filters as {@link #adminQuery}. */
    long adminCount(String orderSn, List<Short> orderStatus,
                    java.time.LocalDateTime start, java.time.LocalDateTime end);

    void deleteByOrderId(LitemallOrderId orderId);

    public String generateOrderSn(LitemallUserId userId);

    int count();

    List<LitemallOrderAggregate> queryUnPaid(int minutes);

    /**
     * Wave 21: orders placed against a combination group-buy slot ("pink"), used by
     * the GROUP_EXPIRED listener to find the paid/unpaid orders a failed group
     * affects. Non-deleted rows only; normally at most one order per slot.
     */
    List<LitemallOrderAggregate> findByPinkId(Integer pinkId);

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
    int markPaidIfCreated(LitemallOrderId orderId, String payId, String paymentIntentId);

    /**
     * Record the PaymentIntent minted for an order STILL IN CREATED, so the unpaid-order
     * sweep can reconcile with (and cancel at) the PSP before cancelling the order
     * (plan-order-lifecycle-e2e.md, package A). Latest intent wins — a retried checkout
     * mints a new one and the previous is cancelled at the PSP by the caller. The UNIQUE
     * index on the column still forbids one intent on two orders. Returns rows updated
     * (0 when the order left CREATED meanwhile — the caller must not hand out a client
     * secret for an order that can no longer be paid).
     */
    int recordPaymentIntentIfCreated(LitemallOrderId orderId, String paymentIntentId);

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

    /** Late tracking backfill: stamp carrier/tracking on an order still in SHIPPED (no status change). */
    int updateShipTrackingIfShipped(LitemallOrderId orderId, String shipChannel, String shipSn);

    int markDeliveredIfShipped(LitemallOrderId orderId, java.time.LocalDateTime confirmTime);

    int markAutoDeliveredIfShipped(LitemallOrderId orderId, java.time.LocalDateTime confirmTime);

    /**
     * PAID → DELIVERED by pickup write-off (Wave 4): conditional UPDATE guarded on
     * {@code order_status=201 AND verify_time IS NULL}, stamping verify_time/verified_by.
     * 0 rows = a concurrent scan won / the order moved on — the caller loses cleanly.
     */
    int markDeliveredByWriteoff(LitemallOrderId orderId, String verifiedBy);

    /**
     * Assign the pickup verify code exactly once ({@code verify_code IS NULL} guard,
     * Wave 4). Called inside the payment transaction; a UNIQUE collision with another
     * order's code raises a duplicate-key exception — the caller regenerates and retries.
     */
    int assignVerifyCode(LitemallOrderId orderId, String verifyCode);

    /** Lookup by pickup verify code (admin write-off scan; deleted rows excluded). */
    java.util.Optional<LitemallOrderAggregate> findByVerifyCode(String verifyCode);

    /** PAID or SHIPPED → REFUND_REQUEST. */
    int markRefundRequestedIfPayable(LitemallOrderId orderId, String refundContent);

    /** REFUND_REQUEST → REFUNDED. */
    int markRefundedIfRequested(LitemallOrderId orderId, java.math.BigDecimal refundAmount, java.time.LocalDateTime refundTime);

    /** Customer withdrew the refund request (D4): REFUND_REQUEST → {@code backTo} (PAID or SHIPPED), CAS. */
    int markRefundWithdrawnIfRequested(LitemallOrderId orderId, LitemallOrderStatus backTo);

    void updateAfterSaleStatus(LitemallOrderId orderId, Short statuReject);

    /**
     * Record the CJ identifiers returned by a successful CJ createOrderV2 on a
     * {@code source='cj'} order (see V27/V33), plus the logistics line it was placed with
     * (persisted as {@code ship_channel}) and the initial CJ-side status (normally CREATED).
     * Called from the post-pay placement path (Wave 8: outside the money transaction) —
     * a guarded single-row projection write, safe to auto-commit.
     */
    int recordCjPlacement(LitemallOrderId orderId, String cjOrderId, String cjOrderNum, String shipChannel,
                          String cjOrderStatus);

    /**
     * Persist the last CJ-side status seen by the lifecycle sync (V33). A plain projection
     * write — local {@code order_status} moves only through the guarded transitions above.
     */
    int updateCjOrderStatus(LitemallOrderId orderId, String cjOrderStatus);

    /**
     * Requeue a parked placement (package B, F7): NULL the local park sentinel
     * (PLACEMENT_REJECTED / PLACEMENT_STALLED) on a PAID, unplaced CJ order. CAS — 0 rows
     * when the order is not parked any more.
     */
    int clearCjPlacementSentinel(LitemallOrderId orderId);

    /**
     * Ids of CJ-fulfilled orders the status-sync poll should visit: placed at CJ, CJ status
     * not yet terminal (DELIVERED/CANCELLED), local status not terminal. Least-recently-updated
     * first, capped at {@code limit} per sweep (CJ's ~1 QPS budget).
     */
    List<LitemallOrderId> querySyncableCjOrders(int limit);

    /**
     * Ids of PAID CJ orders not yet placed at CJ (Wave 8) — the durable placement queue.
     * The order row is the job: the predicate never expires, so a paid order survives any
     * CJ outage/disabled window and is placed when CJ returns. Excludes orders parked with
     * the local {@code PLACEMENT_REJECTED} sentinel. Least-recently-updated first, capped
     * at {@code limit}.
     */
    List<LitemallOrderId> queryPlaceableCjOrders(int limit);

    /**
     * {@link #queryPlaceableCjOrders} restricted to admin-APPROVED orders (Wave 23, V59) —
     * the sweep predicate in placement-mode 'manual'. Unapproved orders stay durably
     * queued but never reach CJ.
     */
    List<LitemallOrderId> queryApprovedPlaceableCjOrders(int limit);

    /**
     * Paged pending list for the admin CJ-approval panel (Wave 23): paid CJ orders not
     * yet placed at CJ and not yet approved, oldest payment first. Includes rows parked
     * under the PLACEMENT_REJECTED sentinel (paid, unplaced — they need admin attention).
     */
    List<LitemallOrderAggregate> queryCjPlacementPending(int page, int limit);

    /** Total for {@link #queryCjPlacementPending}. */
    long countCjPlacementPending();

    /**
     * CAS admin-approval stamp for CJ placement (Wave 23, V59): sets
     * {@code cj_placement_approved_time = now(), cj_placement_approved_by = approvedBy}
     * exactly once — only on a PAID, unplaced, non-deleted CJ order whose stamp is still
     * null. Returns the affected-row count (0 = already approved or not eligible; the
     * caller re-reads the row to tell the two apart).
     */
    int stampCjPlacementApproval(LitemallOrderId orderId, String approvedBy);
}
