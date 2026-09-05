package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallUnpaidOrderTaskAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallUnpaidOrderTaskRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Component
public class UnpaidOrderTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderTaskScheduler.class);
    private static final int SWEEP_BATCH_LIMIT = 200;

    private final LitemallUnpaidOrderTaskRepository repository;
    private final UnpaidOrderReconciler reconciler;

    public UnpaidOrderTaskScheduler(LitemallUnpaidOrderTaskRepository repository,
                                    UnpaidOrderReconciler reconciler) {
        this.repository = repository;
        this.reconciler = reconciler;
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

    // @Transactional so claimDueBatch's FOR UPDATE SKIP LOCKED row locks are held
    // for the whole sweep: a concurrent instance's sweep skips these rows, so no
    // order is double-processed. Every order mutation the reconciler makes runs in
    // its OWN transaction (REQUIRES_NEW through a proxy) against the order tables
    // only — never the task table — so a per-row failure rolls back just that
    // order's work without poisoning this claim transaction, and the row is left
    // (undeleted) for retry.
    @Scheduled(fixedDelayString = "${litemall.order.unpaid-sweep-ms:60000}")
    @Transactional
    public void sweep() {
        List<LitemallUnpaidOrderTaskAggregate> due = repository.claimDueBatch(LocalDateTime.now(), SWEEP_BATCH_LIMIT);
        if (due.isEmpty()) {
            return;
        }
        log.info("Unpaid-order sweep: claimed {} due orders", due.size());
        for (LitemallUnpaidOrderTaskAggregate task : due) {
            LitemallOrderId orderId = task.getOrderId();
            try {
                UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(orderId);
                if (outcome.isDeferred()) {
                    // Money in flight, or the PSP could not be asked: keep the row, push it out.
                    task.setDueAt(outcome.getDeferUntil());
                    repository.upsert(task);
                } else {
                    repository.deleteByOrderId(orderId);
                }
            } catch (NoSuchElementException e) {
                // The order is gone (soft-deleted/purged). There is nothing left to
                // cancel and no future sweep can change that, so retrying would loop
                // forever — retire the task instead.
                log.info("Unpaid-order task for missing order {} dropped", orderId.getId());
                repository.deleteByOrderId(orderId);
            } catch (RuntimeException e) {
                // Kept for retry on purpose: this is for TRANSIENT failures. Anything
                // permanent must be made non-throwing at the source, or it loops here.
                log.warn("Failed to resolve unpaid order {}; row left for retry", orderId.getId(), e);
            }
        }
    }
}
