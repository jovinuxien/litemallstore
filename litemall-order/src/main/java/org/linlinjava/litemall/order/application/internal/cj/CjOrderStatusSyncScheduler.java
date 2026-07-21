package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls CJ for the status of open CJ-fulfilled orders and lets {@link CjLifecycleService}
 * advance each one (confirm → payBalance → SHIPPED/DELIVERED mapping + timeline hops).
 * Mirrors {@link org.linlinjava.litemall.order.application.internal.AutoConfirmOrderScheduler}:
 * the source set is read straight off {@code litemall_order}
 * ({@link LitemallOrderRepository#querySyncableCjOrders}), one order's failure never poisons
 * the sweep, and a raced/failed order is simply picked up again next time.
 *
 * <p>The batch cap plus the ~1.1s inter-order pause keep a sweep inside CJ's ~1 QPS
 * account-wide budget (worst case ≈ batch × 2 CJ calls ≈ under a minute for the default 20,
 * well inside the default 5-minute cadence).
 */
@Component
public class CjOrderStatusSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CjOrderStatusSyncScheduler.class);

    private final LitemallOrderRepository orderRepository;
    private final CjLifecycleService lifecycleService;
    private final org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService cjTokenService;

    /** Max open CJ orders visited per sweep (least-recently-updated first — nobody starves). */
    @Value("${litemall.order.cj-sync-batch:20}")
    private int batchSize;

    public CjOrderStatusSyncScheduler(LitemallOrderRepository orderRepository,
                                      CjLifecycleService lifecycleService,
                                      org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService cjTokenService) {
        this.orderRepository = orderRepository;
        this.lifecycleService = lifecycleService;
        this.cjTokenService = cjTokenService;
    }

    @Scheduled(fixedDelayString = "${litemall.order.cj-sync-sweep-ms:300000}")
    public void sweep() {
        if (!cjTokenService.isEnabled()) {
            // CJ disabled: every getOrderDetail would fail anyway — skip without DB/CJ traffic
            // (also kills the per-order WARN spam a bad/absent key used to cause every sweep).
            return;
        }
        List<LitemallOrderId> due = orderRepository.querySyncableCjOrders(batchSize);
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("CJ status-sync sweep: {} open CJ orders", due.size());
        for (LitemallOrderId orderId : due) {
            try {
                lifecycleService.advance(orderId);
            } catch (RuntimeException e) {
                log.warn("CJ status-sync failed for order {}; will retry next sweep", orderId.getId(), e);
            }
            pace();
        }
    }

    /** CJ enforces ~1 QPS account-wide; space the per-order getOrderDetail calls out. */
    private static void pace() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
