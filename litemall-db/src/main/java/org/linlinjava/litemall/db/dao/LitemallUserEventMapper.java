package org.linlinjava.litemall.db.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallConsentRecord;
import org.linlinjava.litemall.db.domain.LitemallUserEvent;

/**
 * Hand-written mapper for the behavioral-event Phase-0 tables
 * ({@code litemall_user_event}, {@code litemall_visitor_identity},
 * {@code litemall_consent_record} — V49). No {@code Example} machinery.
 *
 * <p>All writes are idempotent-by-key ({@code INSERT IGNORE} on the unique keys)
 * so flaky-network client retries and re-delivered server events never duplicate.
 * Contract: {@code doc/behavioral-events.md}.
 */
public interface LitemallUserEventMapper {

    /**
     * Multi-row {@code INSERT IGNORE} of a drained batch; duplicate {@code event_id}s
     * are silently absorbed. Returns rows actually inserted.
     */
    int batchInsertIgnore(@Param("events") List<LitemallUserEvent> events);

    /** Single-row variant for server-origin events (order paid/refund listeners). */
    int insertIgnore(LitemallUserEvent event);

    /**
     * Visitor->user stitching link ({@code INSERT IGNORE} on UNIQUE(visitor_id, user_id));
     * re-links on every later batch are no-ops. Returns 1 only for a NEW link.
     */
    int insertIdentityLinkIgnore(@Param("visitorId") String visitorId,
                                 @Param("userId") int userId,
                                 @Param("linkedAt") LocalDateTime linkedAt);

    /** Append a consent audit row (never updated — newest row is the current state). */
    int insertConsentRecord(LitemallConsentRecord record);

    /** Events for a visitor in a time window, oldest first (verification/read-side). */
    List<LitemallUserEvent> selectByVisitor(@Param("visitorId") String visitorId,
                                            @Param("since") LocalDateTime since,
                                            @Param("limit") int limit);

    /** Events for a user in a time window, oldest first (verification/read-side). */
    List<LitemallUserEvent> selectByUser(@Param("userId") int userId,
                                         @Param("since") LocalDateTime since,
                                         @Param("limit") int limit);
}
