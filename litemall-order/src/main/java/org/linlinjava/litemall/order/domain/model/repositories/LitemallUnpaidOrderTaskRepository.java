package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallUnpaidOrderTaskAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;
import java.util.List;

public interface LitemallUnpaidOrderTaskRepository {

    void upsert(LitemallUnpaidOrderTaskAggregate task);

    void deleteByOrderId(LitemallOrderId orderId);

    List<LitemallUnpaidOrderTaskAggregate> findDue(LocalDateTime now, int limit);
}
