package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallCjDisputeAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for the CJ dispute projection ({@code litemall_cj_dispute}, V28). */
public interface LitemallCjDisputeRepository {

    /** Persist a newly opened dispute; assigns the generated id back onto the aggregate. */
    void add(LitemallCjDisputeAggregate dispute);

    Optional<LitemallCjDisputeAggregate> findById(Integer disputeId);

    /** All disputes for an order, newest first. */
    List<LitemallCjDisputeAggregate> findByOrder(LitemallOrderId orderId);

    /** Disputes neither cancelled nor finally resolved. */
    List<LitemallCjDisputeAggregate> findOpenByOrder(LitemallOrderId orderId);

    /** Persist the CJ-synced projection fields (cj id, status, resolution, amounts). */
    void updateCjProjection(LitemallCjDisputeAggregate dispute);

    void markCancelled(Integer disputeId);
}
