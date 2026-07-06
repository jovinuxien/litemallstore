package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallUnpaidOrderTaskAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallUnpaidOrderTaskRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

// JdbcTemplate-based (not MyBatis): the table is 3 columns and order-private,
// so generating a Mapper in litemall-db is unnecessary boilerplate.
@Repository
public class LitemallUnpaidOrderTaskRepositoryImpl implements LitemallUnpaidOrderTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public LitemallUnpaidOrderTaskRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(LitemallUnpaidOrderTaskAggregate task) {
        jdbcTemplate.update(
                "INSERT INTO litemall_unpaid_order_task(order_id, due_at, created_at) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE due_at = VALUES(due_at)",
                task.getOrderId().getId(),
                Timestamp.valueOf(task.getDueAt()),
                Timestamp.valueOf(task.getCreatedAt() != null ? task.getCreatedAt() : LocalDateTime.now())
        );
    }

    @Override
    public void deleteByOrderId(LitemallOrderId orderId) {
        jdbcTemplate.update(
                "DELETE FROM litemall_unpaid_order_task WHERE order_id = ?",
                orderId.getId()
        );
    }

    // FOR UPDATE SKIP LOCKED: locks the selected due rows for the caller's
    // transaction and skips rows already locked by a concurrent sweep, so two
    // service instances never claim the same task. Requires an active transaction
    // (locks are held until commit) and MySQL 8.0+/InnoDB.
    @Override
    public List<LitemallUnpaidOrderTaskAggregate> claimDueBatch(LocalDateTime now, int limit) {
        return jdbcTemplate.query(
                "SELECT order_id, due_at, created_at FROM litemall_unpaid_order_task " +
                        "WHERE due_at <= ? ORDER BY due_at ASC LIMIT ? FOR UPDATE SKIP LOCKED",
                ROW_MAPPER,
                Timestamp.valueOf(now),
                limit
        );
    }

    private static final RowMapper<LitemallUnpaidOrderTaskAggregate> ROW_MAPPER = (rs, rowNum) -> {
        LitemallUnpaidOrderTaskAggregate task = new LitemallUnpaidOrderTaskAggregate();
        task.setOrderId(new LitemallOrderId(rs.getInt("order_id")));
        task.setDueAt(rs.getTimestamp("due_at").toLocalDateTime());
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            task.setCreatedAt(created.toLocalDateTime());
        }
        return task;
    };
}
