package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Auto-confirms shipped orders the customer never confirmed: once an order has been
 * SHIPPED for longer than the grace window it transitions to AUTO_DELIVERED, closing
 * the lifecycle without manual action. Mirrors {@link UnpaidOrderTaskScheduler}, but
 * the source set is read straight off {@code litemall_order} (SHIPPED + old ship_time)
 * via {@link LitemallOrderRepository#queryUnconfirm(int)} — no separate task table.
 *
 * <p>Each order is confirmed in its OWN transaction (REQUIRES_NEW inside
 * {@link LitemallOrderServiceImpl#autoConfirmOrder}), so one failure does not poison
 * the sweep. The guarded UPDATE makes the confirm a no-op if the customer confirmed
 * first (a race the sweep simply skips).
 */
@Component
public class AutoConfirmOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmOrderScheduler.class);

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderServiceImpl orderServiceImpl;

    /**
     * Fallback only. The admin-editable {@code litemall_order_unconfirm} system setting
     * (litemall_system, exposed in the admin panel like the unpaid window) is the source of
     * truth when it is set; until 2026-09-05 it was silently ignored while this code default
     * (15) ran and the panel said 7 (plan-order-lifecycle-e2e.md, F14).
     */
    @Value("${litemall.order.auto-confirm-days:15}")
    private int autoConfirmDaysFallback;

    /** The effective grace window: system setting when positive, else the yml/code fallback. */
    int autoConfirmDays() {
        Integer configured = null;
        try {
            configured = org.linlinjava.litemall.core.system.SystemConfig.getOrderUnconfirm();
        } catch (RuntimeException ignored) {
            // SystemConfig not loaded (tests, early boot): fall back
        }
        return configured != null && configured > 0 ? configured : Math.max(1, autoConfirmDaysFallback);
    }

    public AutoConfirmOrderScheduler(LitemallOrderRepository orderRepository,
                                     LitemallOrderServiceImpl orderServiceImpl) {
        this.orderRepository = orderRepository;
        this.orderServiceImpl = orderServiceImpl;
    }

    @Scheduled(fixedDelayString = "${litemall.order.auto-confirm-sweep-ms:3600000}")
    public void sweep() {
        int autoConfirmDays = autoConfirmDays();
        List<LitemallOrderAggregate> due = orderRepository.queryUnconfirm(autoConfirmDays);
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("Auto-confirm sweep: {} shipped orders past the {}-day window", due.size(), autoConfirmDays);
        for (LitemallOrderAggregate order : due) {
            try {
                orderServiceImpl.autoConfirmOrder(order.getOrderId());
            } catch (RuntimeException e) {
                log.warn("Failed to auto-confirm order {}; will retry next sweep",
                        order.getOrderId().getId(), e);
            }
        }
    }
}
