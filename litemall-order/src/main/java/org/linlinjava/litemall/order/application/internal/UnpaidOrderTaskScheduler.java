package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallUnpaidOrderTaskAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallUnpaidOrderTaskRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class UnpaidOrderTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderTaskScheduler.class);
    private static final int SWEEP_BATCH_LIMIT = 200;

    private final LitemallUnpaidOrderTaskRepository repository;
    private final LitemallOrderServiceImpl orderServiceImpl;

    public UnpaidOrderTaskScheduler(LitemallUnpaidOrderTaskRepository repository,
                                    LitemallOrderServiceImpl orderServiceImpl) {
        this.repository = repository;
        this.orderServiceImpl = orderServiceImpl;
    }

    public void schedule(LitemallOrderId orderId) {
        Integer unpaidMinutes = SystemConfig.getOrderUnpaid();
        schedule(orderId, LocalDateTime.now().plusMinutes(unpaidMinutes));
    }

    public void schedule(LitemallOrderId orderId, LocalDateTime dueAt) {
        LitemallUnpaidOrderTaskAggregate task = new LitemallUnpaidOrderTaskAggregate();
        task.setOrderId(orderId);
        task.setDueAt(dueAt);
        task.setCreatedAt(LocalDateTime.now());
        repository.upsert(task);
    }

    public void cancel(LitemallOrderId orderId) {
        repository.deleteByOrderId(orderId);
    }

    @Scheduled(fixedDelayString = "${litemall.order.unpaid-sweep-ms:60000}")
    public void sweep() {
        List<LitemallUnpaidOrderTaskAggregate> due = repository.findDue(LocalDateTime.now(), SWEEP_BATCH_LIMIT);
        if (due.isEmpty()) {
            return;
        }
        log.info("Unpaid-order sweep: {} due orders to cancel", due.size());
        for (LitemallUnpaidOrderTaskAggregate task : due) {
            try {
                orderServiceImpl.cancelOrder(task.getOrderId(), "auto-cancelled: unpaid timeout");
                repository.deleteByOrderId(task.getOrderId());
            } catch (RuntimeException e) {
                log.warn("Failed to auto-cancel unpaid order {}; row left for retry", task.getOrderId().getId(), e);
            }
        }
    }
}
