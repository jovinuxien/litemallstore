package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjRetryableException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single owner of CJ order placement (Wave 8). Placement no longer runs inside the payment
 * transaction: paying settles the money unconditionally, and this service places the order at
 * CJ afterwards — immediately via {@link #placeAsync} (the pay path's afterCommit fast path)
 * and durably via {@link CjPlacementSweepScheduler} (the retry path). The durable "job" is the
 * order row itself ({@code source='cj' AND order_status=201 AND cj_order_id IS NULL}); it never
 * expires, so an order paid while CJ is disabled or down is placed automatically once CJ
 * returns — never stranded, never faked as fulfilled.
 *
 * <p>Deliberately NOT {@code @Transactional} (same rationale as {@link CjLifecycleService}):
 * the CJ HTTP round-trips must not sit inside a DB transaction; every local write is a guarded
 * single-row update that is safe to auto-commit.
 *
 * <p>Failure classification (from {@code CjDropshipOrderFacadeImpl}):
 * <ul>
 *   <li>{@link LitemallCjRetryableException} (incl. CJ-disabled): the order stays in the
 *       placeable set and is retried every sweep, indefinitely. The customer-visible timeline
 *       gets one "deferred" hop (first attempt only — the sweep never spams).</li>
 *   <li>terminal {@link LitemallCjOrderException} (a CJ business rejection): the order is
 *       parked with the local sentinel {@code cj_order_status='PLACEMENT_REJECTED'}, a
 *       {@code cj_placement_failed} timeline hop, and an ops mail. Money is NOT touched —
 *       refund stays a human decision through the existing refund path (user decision
 *       2026-07-20). Requeue from the admin panel ({@code POST .../cj-placement/requeue},
 *       which clears the sentinel — no SQL).</li>
 *   <li>retryable failures that persist: {@link CjFulfilmentIncidentService} warns ops after
 *       an hour and parks the order under {@code PLACEMENT_STALLED} after a day; same
 *       requeue.</li>
 * </ul>
 *
 * <p>Double-placement defence, in depth: an in-JVM single-flight set (fast path vs sweep),
 * the sweep's reconcile-by-orderNumber pre-check (adopts a CJ order that exists but was never
 * recorded locally — the accepted-but-unparseable case and the crash-after-CJ-accept window),
 * and CJ's own {@code order_sn} dedupe as the cross-instance backstop.
 */
@Service
public class CjPlacementService {

    private static final Logger log = LoggerFactory.getLogger(CjPlacementService.class);

    /** Timeline changeType for placement lifecycle hops (queued / placed / deferred / adopted). */
    public static final String CHANGE_TYPE_CJ_PLACEMENT = "cj_placement";
    /** Timeline changeType for a terminal CJ business rejection (order parked for ops). */
    public static final String CHANGE_TYPE_CJ_PLACEMENT_FAILED = "cj_placement_failed";
    /** Local sentinel in {@code cj_order_status} (only ever set while {@code cj_order_id} is null). */
    public static final String STATUS_PLACEMENT_REJECTED = "PLACEMENT_REJECTED";

    /** Store currency symbol for ops-mail amounts (Wave 24: EUR storewide). */
    private static final String CURRENCY_SYMBOL = "\u20ac";

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final CjFulfillmentService cjFulfillmentService;
    private final CjLifecycleService cjLifecycleService;
    private final CjDropshipOrderFacade cjOrderFacade;
    private final CjOpsNotifier opsNotifier;
    private final CjPlacementMode placementMode;
    private final CjFulfilmentIncidentService incidents;

    /** In-JVM single-flight: the pay fast path and the sweep never place the same order twice. */
    private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

    public CjPlacementService(LitemallOrderRepository orderRepository,
                              LitemallOrderGoodsRepository orderGoodsRepository,
                              LitemallOrderStatusHistoryRepository statusHistoryRepository,
                              CjFulfillmentService cjFulfillmentService,
                              CjLifecycleService cjLifecycleService,
                              CjDropshipOrderFacade cjOrderFacade,
                              CjOpsNotifier opsNotifier,
                              CjPlacementMode placementMode,
                              CjFulfilmentIncidentService incidents) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.cjFulfillmentService = cjFulfillmentService;
        this.cjLifecycleService = cjLifecycleService;
        this.cjOrderFacade = cjOrderFacade;
        this.opsNotifier = opsNotifier;
        this.placementMode = placementMode;
        this.incidents = incidents;
    }

    /** True for either local park sentinel (terminal rejection, or stalled retries). */
    public static boolean isParked(String cjOrderStatus) {
        return STATUS_PLACEMENT_REJECTED.equals(cjOrderStatus)
                || CjFulfilmentIncidentService.STATUS_PLACEMENT_STALLED.equals(cjOrderStatus);
    }

    /**
     * Pay-path fast path: fire-and-forget placement right after the pay transaction commits.
     * Never throws into the caller; any failure is retried by the sweep.
     */
    public void placeAsync(LitemallOrderId orderId) {
        CompletableFuture.runAsync(() -> {
            try {
                place(orderId, false, true);
            } catch (RuntimeException e) {
                log.warn("async CJ placement failed for order {} (sweep will retry): {}",
                        orderId.getId(), e.getMessage());
            }
        });
    }

    /**
     * One placement attempt for one order. Re-checks eligibility against the CURRENT row, so a
     * stale queue read (order refunded/cancelled since) is a clean skip. Never throws.
     *
     * @param reconcileFirst sweep path only: ask CJ for an order under our {@code order_sn}
     *                       before creating one, and adopt it if it exists (costs one CJ call,
     *                       so the fast path — which runs milliseconds after pay — skips it)
     * @param firstAttempt   whether a retryable failure should leave a customer-visible
     *                       "deferred" timeline hop (fast path only; the sweep never spams)
     */
    public void place(LitemallOrderId orderId, boolean reconcileFirst, boolean firstAttempt) {
        if (!inFlight.add(orderId.getId())) {
            log.info("CJ placement for order {} already in flight; skipping", orderId.getId());
            return;
        }
        try {
            LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
            if (order == null || !order.isCjFulfilled()
                    || order.getOrderStatus() != LitemallOrderStatus.PAID
                    || StringUtils.hasText(order.getCjOrderId())
                    || isParked(order.getCjOrderStatus())) {
                return; // paid-and-unplaced only; anything else is not ours (or already parked)
            }
            // F9: a customer asking for their money back must not have the goods shipped
            // under them. Approval refuses this too; the sweep re-checks because the
            // aftersale can open between approval and placement.
            if (hasOpenAftersale(order)) {
                log.info("CJ placement held for order {}: aftersale/refund request open", orderId.getId());
                return;
            }
            // Wave 23 (V59): in manual mode NOTHING places without an admin approval stamp.
            // This guards the pay-path fast placement too (placeAsync callers are unchanged);
            // the order stays in the durable queue and the admin pending list until approved.
            if (placementMode.isManual() && order.getCjPlacementApprovedTime() == null) {
                log.debug("CJ placement held for order {}: awaiting admin approval (manual mode)",
                        orderId.getId());
                return;
            }
            if (reconcileFirst && adoptExistingCjOrder(order)) {
                afterPlacement(orderId);
                return;
            }
            try {
                cjFulfillmentService.placeForPaidOrder(order, orderGoodsRepository.findByOId(orderId));
                recordHop(order, CHANGE_TYPE_CJ_PLACEMENT, "Order placed at CJ for fulfilment");
                afterPlacement(orderId);
            } catch (LitemallCjRetryableException e) {
                log.warn("CJ placement deferred for order {} (retained; sweep retries): {}",
                        orderId.getId(), e.getMessage());
                // One customer-visible "deferred" hop on the FIRST failure (whichever path
                // sees it), an ops warning after an hour, a park after a day (F8). The
                // incident service keeps that state on the timeline.
                incidents.onRetryablePlacementFailure(order, e.getMessage());
            } catch (LitemallCjOrderException e) {
                log.error("CJ placement TERMINALLY rejected for order {}: {}",
                        orderId.getId(), e.getMessage());
                orderRepository.updateCjOrderStatus(orderId, STATUS_PLACEMENT_REJECTED);
                recordHop(order, CHANGE_TYPE_CJ_PLACEMENT_FAILED,
                        "CJ rejected the fulfilment order: " + e.getMessage());
                opsNotifier.notify("CJ placement rejected — order " + order.getOrderSn(),
                        "CJ terminally rejected placement of PAID order " + orderId.getId()
                                + " (sn " + order.getOrderSn() + ", "
                                + money(order) + ").\n\nCJ said: " + e.getMessage()
                                + "\n\nThe customer's payment is NOT touched. Fix the cause, then Requeue the "
                                + "order in the admin panel (Orders → Pending CJ approval), or refund through "
                                + "the normal refund path.");
            }
        } finally {
            inFlight.remove(orderId.getId());
        }
    }

    /**
     * Sweep pre-check: CJ dedupes on our merchant {@code order_sn}, and {@code getOrderDetail}
     * accepts the merchant id — so an order that WAS accepted by CJ but never recorded locally
     * (unparseable create response, crash between CJ-accept and the local write) is adopted
     * here instead of being created twice.
     */
    private boolean adoptExistingCjOrder(LitemallOrderAggregate order) {
        CjOrderSnapshot snapshot = cjOrderFacade.fetchOrderDetail(order.getOrderSn()).orElse(null);
        pace(); // one CJ call spent either way; keep the follow-up (create or advance) paced
        if (snapshot == null || !StringUtils.hasText(snapshot.getCjOrderId())) {
            return false;
        }
        String status = StringUtils.hasText(snapshot.getOrderStatus())
                ? snapshot.getOrderStatus().trim().toUpperCase() : "CREATED";
        orderRepository.recordCjPlacement(order.getOrderId(), snapshot.getCjOrderId(), null,
                snapshot.getLogisticName(), status);
        recordHop(order, CHANGE_TYPE_CJ_PLACEMENT,
                "Adopted existing CJ order " + snapshot.getCjOrderId() + " (reconciled by order number)");
        log.info("CJ placement reconciled for order {} (sn {}): adopted CJ order {} in status {}",
                order.getOrderId().getId(), order.getOrderSn(), snapshot.getCjOrderId(), status);
        return true;
    }

    /**
     * Post-placement follow-up: if the order left PAID while the CJ call was in flight
     * (refund/cancel race), immediately delete the fresh CJ draft; otherwise run the first
     * lifecycle pass (confirm + config-gated payBalance) that used to hang off the pay commit.
     */
    private void afterPlacement(LitemallOrderId orderId) {
        LitemallOrderAggregate fresh = orderRepository.findById(orderId).orElse(null);
        if (fresh == null) {
            return;
        }
        if (fresh.getOrderStatus() != LitemallOrderStatus.PAID) {
            log.warn("order {} left PAID (now {}) while CJ placement was in flight — deleting the CJ draft",
                    orderId.getId(), fresh.getOrderStatus());
            cjFulfillmentService.cancelAtCjIfDeletable(fresh, "cancelled/refunded during placement");
            return;
        }
        pace();
        try {
            cjLifecycleService.advance(orderId);
        } catch (RuntimeException e) {
            log.warn("post-placement CJ lifecycle pass failed for order {} (sweep will retry): {}",
                    orderId.getId(), e.getMessage());
        }
    }

    private static boolean hasOpenAftersale(LitemallOrderAggregate order) {
        var status = order.getAfterSaleStatus();
        return status == org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus.STATUS_REQUEST
                || status == org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus.STATUS_RECEPT;
    }

    /** Same-status timeline hop (the order's local status never moves here). */
    private void recordHop(LitemallOrderAggregate order, String changeType, String message) {
        LitemallOrderStatus local = order.getOrderStatus();
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                order.getOrderId(), local, local, changeType, message, "system", LocalDateTime.now()));
    }

    /** Ops-mail amount. Single-currency store (Wave 24: EUR storewide) — see CURRENCY_SYMBOL. */
    private static String money(LitemallOrderAggregate order) {
        return order.getActualPrice() == null || order.getActualPrice().getAmount() == null
                ? "amount unknown" : CURRENCY_SYMBOL + order.getActualPrice().getAmount().toPlainString();
    }

    /** CJ enforces ~1 QPS account-wide; pause between consecutive CJ calls. */
    private static void pace() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
