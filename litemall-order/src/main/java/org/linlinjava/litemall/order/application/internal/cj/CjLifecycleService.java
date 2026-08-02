package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * CJ order lifecycle sync (Wave 3, Task D): one idempotent {@link #advance} pass that pulls the
 * CJ-side status of a placed order ({@code getOrderDetail}) and (a) records every CJ status hop
 * on the order's timeline ({@code litemall_order_status}, changeType {@code cj_sync}), (b) drives
 * the CJ order forward — confirm a CREATED draft, pay an UNPAID one from balance (config-gated) —
 * and (c) maps CJ fulfilment progress into the local state machine: SHIPPED →
 * {@code shipOrder} (persisting CJ's trackNumber as {@code ship_sn}), DELIVERED →
 * auto-confirm. Called from the post-payment commit hook (first pass, right after placement)
 * and from {@link CjOrderStatusSyncScheduler} (every following pass) — any pass that fails is
 * simply retried by the next sweep.
 *
 * <p>Deliberately NOT {@code @Transactional}: the local mutations run through the same guarded
 * single-row transitions the rest of the module uses ({@code markShippedIfPaid} etc. — safe to
 * auto-commit statement-by-statement), and {@code autoConfirmOrder} opens its own REQUIRES_NEW
 * transaction, which inside an outer transaction holding a lock on the same order row would
 * self-deadlock. CJ statuses this pass doesn't recognize are recorded as hops and otherwise
 * ignored. See docs/adr-cj-lifecycle-parity.md.
 */
@Service
public class CjLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CjLifecycleService.class);

    /** Timeline changeType for CJ-side hops (rendered alongside create/pay/ship/...). */
    public static final String CHANGE_TYPE_CJ_SYNC = "cj_sync";

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final LitemallOrderServiceImpl orderServiceImpl;
    private final CjDropshipOrderFacade cjOrderFacade;
    private final CjOpsNotifier opsNotifier;
    /** When false, UNPAID CJ orders are left for manual payment (CJ dashboard / balance top-up). */
    private final boolean autoPayBalance;

    public CjLifecycleService(LitemallOrderRepository orderRepository,
                              LitemallOrderStatusHistoryRepository statusHistoryRepository,
                              LitemallOrderServiceImpl orderServiceImpl,
                              CjDropshipOrderFacade cjOrderFacade,
                              CjOpsNotifier opsNotifier,
                              @Value("${spring.cjdropship.api.auto-pay-balance:true}") boolean autoPayBalance) {
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.orderServiceImpl = orderServiceImpl;
        this.cjOrderFacade = cjOrderFacade;
        this.opsNotifier = opsNotifier;
        this.autoPayBalance = autoPayBalance;
    }

    /**
     * One sync pass for one order. Never throws — a CJ miss or a lost local race is left for
     * the next sweep. At most one CJ mutation (confirm OR pay) per pass, so a sweep stays
     * inside CJ's ~1 QPS budget and each hop lands on the timeline before the next one starts.
     */
    public void advance(LitemallOrderId orderId) {
        LitemallOrderAggregate order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.isCjFulfilled() || !StringUtils.hasText(order.getCjOrderId())) {
            return;
        }
        CjOrderSnapshot snapshot = cjOrderFacade.fetchOrderDetail(order.getCjOrderId()).orElse(null);
        if (snapshot == null || !StringUtils.hasText(snapshot.getOrderStatus())) {
            return; // CJ unreachable / unusable answer — retry next sweep
        }
        String cjStatus = snapshot.getOrderStatus().trim().toUpperCase();
        String lastSeen = order.getCjOrderStatus(); // pre-hop value: detects the transition below
        recordHopIfChanged(order, cjStatus);

        switch (cjStatus) {
            case "CREATED":
            case "IN_CART":
                pace();
                if (cjOrderFacade.confirmOrder(order.getCjOrderId())) {
                    log.info("CJ order {} (local {}) confirmed", order.getCjOrderId(), orderId.getId());
                }
                break;
            case "UNPAID":
                if (autoPayBalance) {
                    pace();
                    if (cjOrderFacade.payBalance(order.getCjOrderId())) {
                        log.info("CJ order {} (local {}) paid from balance", order.getCjOrderId(), orderId.getId());
                    }
                } else {
                    log.info("CJ order {} (local {}) awaits manual payment (auto-pay-balance=false)",
                            order.getCjOrderId(), orderId.getId());
                }
                break;
            case "SHIPPED":
                shipLocallyIfPaid(order, snapshot);
                backfillTrackingIfMissing(order, snapshot);
                break;
            case "DELIVERED":
                shipLocallyIfPaid(order, snapshot);
                backfillTrackingIfMissing(order, snapshot);
                confirmLocallyIfShipped(order);
                break;
            case "CANCELLED":
                log.warn("CJ order {} (local {}) is CANCELLED at CJ — local order stays {}; "
                                + "refund via the existing aftersale/refund paths",
                        order.getCjOrderId(), orderId.getId(), order.getOrderStatus());
                // Ops attention exactly once, on the transition (Wave 8; user decision
                // 2026-07-20: notify + timeline, no automatic money movement). The hop and
                // the cj_order_status projection were already written by recordHopIfChanged.
                if (!"CANCELLED".equals(lastSeen)) {
                    opsNotifier.notify("CJ cancelled order " + order.getOrderSn(),
                            "CJ reports order " + orderId.getId() + " (sn " + order.getOrderSn()
                                    + ", CJ id " + order.getCjOrderId() + ") as CANCELLED on the CJ side."
                                    + "\nLocal order status: " + order.getOrderStatus()
                                    + " — the customer's payment is NOT touched automatically."
                                    + "\nDecide and settle via the normal refund/aftersale path.");
                }
                break;
            default:
                // UNSHIPPED (incl. PENDING/PROCESSING sub-states) and anything CJ adds later:
                // the hop is on the timeline; nothing local to advance yet.
                break;
        }
    }

    /**
     * Persist the CJ status projection + timeline hop when CJ moved since the last pass.
     * Marker first, then the projection write: if the second fails the next sweep records a
     * duplicate marker rather than silently losing a hop.
     */
    private void recordHopIfChanged(LitemallOrderAggregate order, String cjStatus) {
        String lastSeen = order.getCjOrderStatus();
        if (cjStatus.equals(lastSeen)) {
            return;
        }
        LitemallOrderStatus local = order.getOrderStatus();
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                order.getOrderId(), local, local, CHANGE_TYPE_CJ_SYNC,
                "CJ order status: " + (lastSeen == null ? "(placed)" : lastSeen) + " -> " + cjStatus,
                "system", LocalDateTime.now()));
        orderRepository.updateCjOrderStatus(order.getOrderId(), cjStatus);
        order.setCjOrderStatus(cjStatus);
        log.info("CJ sync: order {} (cj {}) {} -> {}", order.getOrderId().getId(),
                order.getCjOrderId(), lastSeen == null ? "(placed)" : lastSeen, cjStatus);
    }

    /** PAID → SHIPPED with CJ's carrier + tracking number; a lost race just logs and moves on. */
    private void shipLocallyIfPaid(LitemallOrderAggregate order, CjOrderSnapshot snapshot) {
        if (order.getOrderStatus() != LitemallOrderStatus.PAID) {
            return;
        }
        String carrier = firstNonBlank(snapshot.getTrackingProvider(), snapshot.getLogisticName(),
                order.getShipChannel(), "CJ");
        String trackNumber = snapshot.getTrackNumber() == null ? "" : snapshot.getTrackNumber();
        try {
            orderServiceImpl.shipOrder(order.getOrderId(), carrier, trackNumber);
            order.setOrderStatus(LitemallOrderStatus.SHIPPED); // keep the in-memory view current
            log.info("CJ sync: order {} shipped via {} ({})", order.getOrderId().getId(), carrier, trackNumber);
        } catch (RuntimeException e) {
            log.warn("CJ sync: could not ship order {} locally: {}", order.getOrderId().getId(), e.getMessage());
        }
    }

    /**
     * CJ can report SHIPPED before assigning a tracking number, so the ship pass may
     * have written an empty ship_sn. Once CJ has the number, stamp it on the
     * still-SHIPPED order — the service re-publishes the shipped event, which sends
     * the customer the tracking-number email, and the hop lands on the timeline.
     */
    private void backfillTrackingIfMissing(LitemallOrderAggregate order, CjOrderSnapshot snapshot) {
        if (order.getOrderStatus() != LitemallOrderStatus.SHIPPED
                || StringUtils.hasText(order.getShipSn())
                || !StringUtils.hasText(snapshot.getTrackNumber())) {
            return;
        }
        String carrier = firstNonBlank(snapshot.getTrackingProvider(), snapshot.getLogisticName(),
                order.getShipChannel(), "CJ");
        try {
            if (orderServiceImpl.backfillShipTracking(order.getOrderId(), carrier, snapshot.getTrackNumber())) {
                order.setShipSn(snapshot.getTrackNumber()); // keep the in-memory view current
                statusHistoryRepository.record(new LitemallOrderStatusChange(
                        order.getOrderId(), LitemallOrderStatus.SHIPPED, LitemallOrderStatus.SHIPPED,
                        CHANGE_TYPE_CJ_SYNC, "Tracking number assigned: " + snapshot.getTrackNumber()
                        + " (" + carrier + ")", "system", LocalDateTime.now()));
                log.info("CJ sync: order {} tracking backfilled: {} ({})",
                        order.getOrderId().getId(), snapshot.getTrackNumber(), carrier);
            }
        } catch (RuntimeException e) {
            log.warn("CJ sync: could not backfill tracking on order {}: {}",
                    order.getOrderId().getId(), e.getMessage());
        }
    }

    /** SHIPPED → AUTO_DELIVERED once CJ reports DELIVERED; skips cleanly on a race. */
    private void confirmLocallyIfShipped(LitemallOrderAggregate order) {
        if (order.getOrderStatus() != LitemallOrderStatus.SHIPPED) {
            return;
        }
        try {
            orderServiceImpl.autoConfirmOrder(order.getOrderId());
            log.info("CJ sync: order {} auto-confirmed (CJ DELIVERED)", order.getOrderId().getId());
        } catch (RuntimeException e) {
            log.warn("CJ sync: could not auto-confirm order {}: {}", order.getOrderId().getId(), e.getMessage());
        }
    }

    /** CJ enforces ~1 QPS account-wide; pause before a follow-up CJ call in the same pass. */
    private static void pace() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return "";
    }
}
