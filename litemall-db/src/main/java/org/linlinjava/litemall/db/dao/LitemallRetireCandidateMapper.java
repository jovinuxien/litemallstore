package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;

/**
 * Hand-written mapper for scored retirement (off-sale) proposals
 * ({@code litemall_retire_candidate}, V46; UNIQUE goods_id + day).
 * No {@code Example} machinery. Proposals only act via admin approval; the daily
 * executor flips due approved rows off-sale.
 */
public interface LitemallRetireCandidateMapper {

    /**
     * Insert a proposal or refresh the same day's scoring — but ONLY while the row is still
     * {@code proposed}; an approved/dismissed/executed decision is never overwritten by a re-run.
     */
    int upsertProposal(LitemallRetireCandidate candidate);

    /** All candidates for a day (any status unless given), best (highest) score first. */
    List<LitemallRetireCandidate> selectByDay(@Param("day") LocalDate day, @Param("status") String status);

    /** Candidates in a status across all days, newest day / highest score first. */
    List<LitemallRetireCandidate> selectByStatus(@Param("status") String status, @Param("limit") int limit);

    /** Latest candidate row for a goods (any day, any status), newest day first, or null. */
    LitemallRetireCandidate selectLatestByGoods(@Param("goodsId") int goodsId);

    /** Latest still-{@code proposed} row for a goods, newest day first, or null. */
    LitemallRetireCandidate selectLatestProposedByGoods(@Param("goodsId") int goodsId);

    /** Approved rows whose execute_on has arrived ({@code execute_on <= today}). */
    List<LitemallRetireCandidate> selectDueApproved(@Param("today") LocalDate today);

    /**
     * Guarded status transition: only applies when the row currently has {@code expect} status.
     * {@code executeOn} is written only when non-null (approve sets it; dismiss/execute keep it).
     * Returns affected rows (0 = the guard failed — already decided).
     */
    int updateStatus(@Param("id") int id, @Param("expect") String expect, @Param("status") String status,
                     @Param("executeOn") LocalDate executeOn);
}
