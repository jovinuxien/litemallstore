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

    /** Days a SHIPPED order waits for customer confirmation before auto-confirming. */
    @Value("${litemall.order.auto-confirm-days:15}")
    private int autoConfirmDays;

    public AutoConfirmOrderScheduler(LitemallOrderRepository orderRepository,
                                     LitemallOrderServiceImpl orderServiceImpl) {
        this.orderRepository = orderRepository;
        this.orderServiceImpl = orderServiceImpl;
    }

    @Scheduled(fixedDelayString = "${litemall.order.auto-confirm-sweep-ms:3600000}")
    public void sweep() {
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
