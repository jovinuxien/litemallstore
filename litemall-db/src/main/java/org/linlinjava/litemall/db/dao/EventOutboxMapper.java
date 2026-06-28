package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallEventOutbox;

import java.util.List;

/**
 * Hand-written mapper for the transactional outbox ({@code litemall_event_outbox}, V25).
 * Co-located with the generated DAOs so it is picked up by {@code @MapperScan} and the
 * {@code dao/*.xml} mapper-location pattern.
 */
public interface EventOutboxMapper {

    /** Append one event (status PENDING). Populates the generated id back onto the record. */
    int insert(LitemallEventOutbox record);

    /** Oldest pending events first, capped at {@code limit} — the relay batch. */
    List<LitemallEventOutbox> selectPending(@Param("limit") int limit);

    /** Mark a row delivered. */
    int markSent(@Param("id") Long id);

    /** Record a failed attempt; the row stays PENDING until {@code maxAttempts}, then FAILED. */
    int markFailed(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);
}