package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;

/**
 * Hand-written mapper for scored coupon/groupon merchandising proposals
 * ({@code litemall_promo_candidate}, V53; UNIQUE kind + goods_id + day).
 * No {@code Example} machinery. Proposals only become promotions via the
 * existing promotion admin create paths; the insight endpoint records that
 * with a guarded flip to {@code consumed}.
 */
public interface LitemallPromoCandidateMapper {

    /**
     * Insert a proposal or refresh the same day's scoring — but ONLY while the row is
     * still {@code proposed}; a consumed/dismissed decision is never overwritten.
     */
    int upsertProposal(LitemallPromoCandidate candidate);

    /** Candidates of a kind for a day (any status unless given), score desc. */
    List<LitemallPromoCandidate> selectByKindAndDay(@Param("kind") String kind,
                                                    @Param("day") LocalDate day,
                                                    @Param("status") String status);

    /** Latest day that has any rows for the kind, or null (drives the list default). */
    LocalDate selectLatestDay(@Param("kind") String kind);

    /** One kind+goods+day row, or null. */
    LitemallPromoCandidate selectByKindGoodsAndDay(@Param("kind") String kind,
                                                   @Param("goodsId") int goodsId,
                                                   @Param("day") LocalDate day);

    /** Latest row for a kind+goods (any day), newest day first, or null. */
    LitemallPromoCandidate selectLatestByKindAndGoods(@Param("kind") String kind,
                                                      @Param("goodsId") int goodsId);

    /**
     * Guarded status transition: only applies when the row currently has {@code expect}
     * status. {@code refId} is stored when non-null (consume records the created
     * coupon/combination id). Returns affected rows (0 = guard failed — already decided).
     */
    int updateStatus(@Param("id") int id, @Param("expect") String expect,
                     @Param("status") String status, @Param("refId") Integer refId);
}
