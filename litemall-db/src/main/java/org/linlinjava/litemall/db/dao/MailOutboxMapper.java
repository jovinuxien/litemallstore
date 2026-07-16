package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written mapper for the transactional customer-mail outbox
 * ({@code litemall_mail_outbox}, V41). Mirrors {@link CjSourcingRequestMapper}:
 * co-located with the generated DAOs so the existing {@code @MapperScan} +
 * {@code dao/*.xml} mapper-location pattern picks it up.
 *
 * <p>Status transitions are GUARDED conditional updates (0 rows = the row was
 * not in the expected state — a concurrent sweep or resend won the race).
 */
public interface MailOutboxMapper {

    /** Insert a new outbox row; populates the generated id back onto the record. */
    int insert(LitemallMailOutbox record);

    LitemallMailOutbox selectById(@Param("id") Integer id);

    /**
     * Sendable batch for the sweep: pending rows whose {@code send_at} has passed,
     * oldest id first (a future send_at row stays invisible until its time — the
     * scheduled-send seam).
     */
    List<LitemallMailOutbox> findSendable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** pending → sent. Guarded: 0 rows when the row is no longer pending. */
    int markSent(@Param("id") Integer id, @Param("now") LocalDateTime now);

    /** pending → failed (terminal until an admin resend), recording the last error and the final attempt. */
    int markFailed(@Param("id") Integer id, @Param("lastError") String lastError, @Param("now") LocalDateTime now);

    /** Failed delivery attempt below the cap: attempts+1 + last_error, row stays pending. */
    int incrementAttempts(@Param("id") Integer id, @Param("lastError") String lastError, @Param("now") LocalDateTime now);

    /**
     * Admin resend: failed → pending with attempts zeroed, last_error cleared and
     * send_at reset to now. Guarded to failed rows only — 0 rows = not resendable.
     */
    int resetForResend(@Param("id") Integer id, @Param("now") LocalDateTime now);

    /** Page of (non-deleted) rows, newest first, optionally filtered by status. */
    List<LitemallMailOutbox> selectPage(@Param("status") String status,
                                        @Param("offset") int offset, @Param("limit") int limit);

    int countByStatus(@Param("status") String status);
}
