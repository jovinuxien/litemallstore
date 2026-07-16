package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallUserBrokerageRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written slim mapper for the commission ledger
 * ({@code litemall_user_brokerage_record}, V7 — unmapped until Wave 5). Mirrors
 * {@link CjSourcingRequestMapper}: co-located with the generated DAOs so the existing
 * {@code @MapperScan} + {@code dao/*.xml} mapper-location pattern picks it up; no
 * Example classes.
 *
 * <p>Every status transition is a GUARDED UPDATE (0-row result = lost race or wrong
 * state — callers must treat it as "someone else got there first" and never
 * retry-credit).
 */
public interface LitemallUserBrokerageRecordMapper {

    /** Insert a ledger row; populates the generated id back onto the record. */
    int insert(LitemallUserBrokerageRecord record);

    LitemallUserBrokerageRecord selectById(@Param("id") Integer id);

    /** Page of a user's ledger, newest first ({@code add_time desc}). */
    List<LitemallUserBrokerageRecord> selectPageByUserId(@Param("userId") Integer userId,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    long countByUserId(@Param("userId") Integer userId);

    /** Sum of {@code price} for one (status, pm) bucket; 0 when empty (never null). */
    BigDecimal sumByUserAndStatus(@Param("userId") Integer userId,
                                  @Param("status") byte status,
                                  @Param("pm") boolean pm);

    /** Commission earned (pm=1, not invalidated) with {@code add_time >= since}. */
    BigDecimal sumEarnedSince(@Param("userId") Integer userId,
                              @Param("since") LocalDateTime since);

    /** Orders that produced a (not invalidated) commission row for this user. */
    long countReferredOrders(@Param("userId") Integer userId);

    /** Frozen rows whose freeze window has elapsed ({@code unfreeze_time <= now}). */
    List<LitemallUserBrokerageRecord> selectUnfreezable(@Param("now") LocalDateTime now,
                                                        @Param("limit") int limit);

    /** Guarded FROZEN(0) → VALID(1); 0 rows = raced or already transitioned. */
    int markValidFromFrozen(@Param("id") Integer id);

    /**
     * Guarded FROZEN(0) → INVALID(-1) for the commission of one order (aftersale
     * clawback). An already-unfrozen row stays VALID by design (see the brokerage
     * lifecycle ADR); 0 rows = no frozen commission to claw back.
     */
    int invalidateFrozenByOrder(@Param("orderSn") String orderSn);

    /** The pm=0 withdrawal-debit row of one extract request, if brokerage-sourced. */
    LitemallUserBrokerageRecord selectExtractDebit(@Param("extractId") Integer extractId);

    /** Guarded VALID(1) → INVALID(-1) on a pm=0 extract-debit row (extract rejected). */
    int invalidateExtractDebit(@Param("id") Integer id);
}
