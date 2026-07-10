package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.util.List;
import java.util.Optional;

/** Persistence port for aftersale/RMA applications (legacy litemall_aftersale table). */
public interface LitemallAftersaleRepository {

    Optional<LitemallAftersaleAggregate> findById(Integer aftersaleId);

    /** All (non-deleted) applications for an order, newest first. */
    List<LitemallAftersaleAggregate> findByOrder(LitemallOrderId orderId);

    /** The order's still-undecided application, if any (one open application at a time). */
    Optional<LitemallAftersaleAggregate> findOpenByOrder(LitemallOrderId orderId);

    /** How many applications this order ever had — feeds the {orderSn}-A{n} sn. */
    long countByOrder(LitemallOrderId orderId);

    /** Admin queue, newest first. Every filter is optional (null = don't filter). */
    List<LitemallAftersaleAggregate> adminQuery(Short status, Integer orderId, Integer userId,
                                                int page, int limit);

    long adminCount(Short status, Integer orderId, Integer userId);

    /** Insert a new application; returns it with the generated id set. */
    LitemallAftersaleAggregate add(LitemallAftersaleAggregate aftersale);

    /** Persist a lifecycle change (status / handle_time / update_time). */
    void update(LitemallAftersaleAggregate aftersale);
}
