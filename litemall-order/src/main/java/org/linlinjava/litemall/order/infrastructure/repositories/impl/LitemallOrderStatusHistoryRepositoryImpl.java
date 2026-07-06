package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.OrderStatusLogMapper;
import org.linlinjava.litemall.db.domain.LitemallOrderStatusLog;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Backs the status-history timeline onto the litemall-db {@link OrderStatusLogMapper}.
 * Maps the order-domain {@link LitemallOrderStatusChange} VO to/from the litemall-db
 * {@link LitemallOrderStatusLog} row.
 */
@Repository
public class LitemallOrderStatusHistoryRepositoryImpl implements LitemallOrderStatusHistoryRepository {

    private final OrderStatusLogMapper mapper;

    public LitemallOrderStatusHistoryRepositoryImpl(OrderStatusLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(LitemallOrderStatusChange change) {
        LitemallOrderStatusLog row = new LitemallOrderStatusLog();
        row.setOrderId(change.getOrderId().getId());
        row.setOldStatus(change.getFromStatus() == null ? null : change.getFromStatus().getCode());
        row.setNewStatus(change.getToStatus() == null ? null : change.getToStatus().getCode());
        row.setChangeType(change.getChangeType());
        row.setChangeMessage(change.getChangeMessage());
        LocalDateTime when = change.getChangeTime() == null ? LocalDateTime.now() : change.getChangeTime();
        row.setChangeTime(when);
        row.setOperator(change.getOperator());
        row.setAddTime(when);
        row.setUpdateTime(when);
        row.setDeleted(false);
        mapper.insert(row);
    }

    @Override
    public List<LitemallOrderStatusChange> findByOrderId(LitemallOrderId orderId) {
        return mapper.selectByOrderId(orderId.getId()).stream()
                .map(row -> new LitemallOrderStatusChange(
                        new LitemallOrderId(row.getOrderId()),
                        row.getOldStatus() == null ? null : LitemallOrderStatus.fromCode(row.getOldStatus()),
                        row.getNewStatus() == null ? null : LitemallOrderStatus.fromCode(row.getNewStatus()),
                        row.getChangeType(),
                        row.getChangeMessage(),
                        row.getOperator(),
                        row.getChangeTime()))
                .collect(Collectors.toList());
    }

    @Override
    public int countByOrderId(LitemallOrderId orderId) {
        return mapper.countByOrderId(orderId.getId());
    }
}
