package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallDealCandidate;

/**
 * Hand-written mapper for scored flash-deal proposals
 * ({@code litemall_deal_candidate}, V45; UNIQUE goods_id + day).
 * No {@code Example} machinery. Proposals only become deals via admin approval.
 */
public interface LitemallDealCandidateMapper {

    /**
     * Insert a proposal or refresh the same day's scoring — but ONLY while the row is still
     * {@code proposed}; an approved/dismissed decision is never overwritten by a re-run.
     */
    int upsertProposal(LitemallDealCandidate candidate);

    /** All candidates for a day (any status unless given), joined data comes via InsightMapper. */
    List<LitemallDealCandidate> selectByDay(@Param("day") LocalDate day, @Param("status") String status);

    /** Latest candidate row for a goods (any day), newest day first, or null. */
    LitemallDealCandidate selectLatestByGoods(@Param("goodsId") int goodsId);

    /** One goods+day row, or null. */
    LitemallDealCandidate selectByGoodsAndDay(@Param("goodsId") int goodsId, @Param("day") LocalDate day);

    /**
     * Guarded status transition: only applies when the row currently has {@code expect} status.
     * Returns affected rows (0 = the guard failed — already decided).
     */
    int updateStatus(@Param("id") int id, @Param("expect") String expect, @Param("status") String status);
}
