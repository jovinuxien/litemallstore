package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Durable retry loop for CJ order placement (Wave 8): sweeps PAID CJ orders that have no
 * {@code cj_order_id} yet ({@link LitemallOrderRepository#queryPlaceableCjOrders}) and hands
 * each to {@link CjPlacementService#place} with the reconcile-by-orderNumber pre-check on.
 * Mirrors {@link CjOrderStatusSyncScheduler}: least-recently-updated first, one order's
 * failure never poisons the sweep, ~1.1s pacing keeps a sweep inside CJ's ~1 QPS budget.
 *
 * <p>While the CJ ACL is disabled (no credentials) the sweep skips WITHOUT touching the DB or
 * CJ — the queue predicate never expires, so every paid order is still there when the key
 * appears and gets placed on the first enabled sweep. That is the whole "paid today, placed
 * tomorrow" guarantee.
 */
@Component
public class CjPlacementSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(CjPlacementSweepScheduler.class);

    private final LitemallOrderRepository orderRepository;
    private final CjPlacementService placementService;
    private final CjTokenService cjTokenService;

    /** Max unplaced CJ orders visited per sweep (each costs up to ~3 CJ calls). */
    @Value("${litemall.order.cj-place-batch:10}")
    private int batchSize;

    public CjPlacementSweepScheduler(LitemallOrderRepository orderRepository,
                                     CjPlacementService placementService,
                                     CjTokenService cjTokenService) {
        this.orderRepository = orderRepository;
        this.placementService = placementService;
        this.cjTokenService = cjTokenService;
    }

    @Scheduled(fixedDelayString = "${litemall.order.cj-place-sweep-ms:300000}")
    public void sweep() {
        if (!cjTokenService.isEnabled()) {
            return; // CJ disabled: orders are retained by the queue predicate; nothing to do
        }
        List<LitemallOrderId> due = orderRepository.queryPlaceableCjOrders(batchSize);
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("CJ placement sweep: {} paid-but-unplaced CJ orders", due.size());
        for (LitemallOrderId orderId : due) {
            try {
                placementService.place(orderId, true, false);
            } catch (RuntimeException e) {
                log.warn("CJ placement sweep failed for order {}; will retry next sweep",
                        orderId.getId(), e);
            }
            pace();
        }
    }

    /** CJ enforces ~1 QPS account-wide; space the per-order placement attempts out. */
    private static void pace() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
