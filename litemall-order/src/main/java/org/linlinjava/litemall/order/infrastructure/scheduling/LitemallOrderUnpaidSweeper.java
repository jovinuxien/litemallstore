package org.linlinjava.litemall.order.infrastructure.scheduling;

import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderCancelCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Replaces the legacy in-memory {@code DelayQueue}-based {@code OrderUnpaidTask}
 * (from wx-api / litemall-core) with a DB-backed sweep. Runs on a fixed
 * schedule, picks up orders that have been CREATED but unpaid past
 * {@link SystemConfig#getOrderUnpaid()} minutes, and cancels each via the
 * orchestrator. Survives restarts (no in-process state) and scales
 * horizontally (each instance only cancels what the DB still reports as
 * unpaid; the orchestrator's @Transactional + status check is the
 * race-avoidance boundary).
 */
@Component
public class LitemallOrderUnpaidSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(LitemallOrderUnpaidSweeper.class);
    private static final String CANCEL_REASON = "System cancellation: unpaid timeout";

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderOrchestratorService orchestrator;

    public LitemallOrderUnpaidSweeper(LitemallOrderRepository orderRepository,
                                      LitemallOrderOrchestratorService orchestrator) {
        this.orderRepository = orderRepository;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${litemall.order.unpaid-sweep-interval-ms:60000}",
            initialDelayString = "${litemall.order.unpaid-sweep-initial-delay-ms:30000}")
    public void sweepUnpaidOrders() {
        int thresholdMinutes = SystemConfig.getOrderUnpaid();
        List<LitemallOrderAggregate> expired = orderRepository.queryUnPaid(thresholdMinutes);
        if (expired.isEmpty()) {
            return;
        }
        LOGGER.info("Unpaid-order sweep: cancelling {} order(s) past {}m threshold", expired.size(), thresholdMinutes);
        for (LitemallOrderAggregate order : expired) {
            LitemallOrderId orderId = order.getOrderId();
            LitemallUserId userId = order.getUserId();
            try {
                orchestrator.cancelOrder(new LitemallOrderCancelCommand(orderId, userId, CANCEL_REASON));
            } catch (Exception e) {
                LOGGER.warn("Unpaid-order sweep: failed to cancel order {} — {}", orderId.getId(), e.getMessage());
            }
        }
    }
}
