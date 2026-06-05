package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallUnpaidOrderTaskAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;
import java.util.List;

public interface LitemallUnpaidOrderTaskRepository {

    void upsert(LitemallUnpaidOrderTaskAggregate task);

    void deleteByOrderId(LitemallOrderId orderId);

    /**
     * Atomically claim up to {@code limit} due tasks for the CURRENT transaction
     * using {@code FOR UPDATE SKIP LOCKED}: the returned rows are row-locked until
     * the caller's transaction commits, so a concurrent sweep on another service
     * instance skips them and the same order is never double-processed. MUST be
     * called within an active transaction.
     */
    List<LitemallUnpaidOrderTaskAggregate> claimDueBatch(LocalDateTime now, int limit);
}
