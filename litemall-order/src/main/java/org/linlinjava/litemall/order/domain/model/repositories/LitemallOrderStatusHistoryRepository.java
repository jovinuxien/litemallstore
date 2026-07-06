package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;

import java.util.List;

/**
 * Append-only store of order state transitions (the {@code litemall_order_status}
 * audit table). Written inside the same transaction as each status change, so the
 * customer/admin timeline is an exact, gap-free record of how an order moved.
 */
public interface LitemallOrderStatusHistoryRepository {

    /** Append one transition. */
    void record(LitemallOrderStatusChange change);

    /** Full history of an order, oldest first (timeline order). */
    List<LitemallOrderStatusChange> findByOrderId(LitemallOrderId orderId);

    /** Number of recorded transitions for an order. */
    int countByOrderId(LitemallOrderId orderId);
}
