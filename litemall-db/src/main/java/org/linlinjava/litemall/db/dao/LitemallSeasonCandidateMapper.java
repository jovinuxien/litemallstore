package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallSeasonCandidate;

/**
 * Hand-written mapper for scored seasonal proposals ({@code litemall_season_candidate}, V64;
 * UNIQUE season_key + goods_id + day). No {@code Example} machinery.
 *
 * <p><b>Where the cap lives.</b> The per-season cap is applied when membership is READ
 * ({@link #selectPublishedGoodsIds}), not when a row is written. Capping at write time looks
 * natural and is wrong: rows already at {@code auto} do not re-enter the count, so a second run
 * on the same day publishes past the cap. Reading the top N by score is idempotent no matter how
 * often the scorer runs.
 */
public interface LitemallSeasonCandidateMapper {

    /**
     * Insert a proposal or refresh the same day's scoring — but ONLY while the row is still
     * {@code proposed} or {@code auto}; a {@code dismissed} veto is never overwritten.
     *
     * <p>The UNIQUE key makes a collision LOUD rather than safe: a plain INSERT would throw and
     * take the whole batch down with it. This upsert is what makes concurrent scoring survivable,
     * and the {@code if(status = ...)} guards are what protect an admin decision.
     */
    int upsertProposal(LitemallSeasonCandidate candidate);

    /** Candidates for a season+day (any status unless given), score desc. */
    List<LitemallSeasonCandidate> selectBySeasonAndDay(@Param("seasonKey") String seasonKey,
                                                       @Param("day") LocalDate day,
                                                       @Param("status") String status);

    /** Latest day holding any rows for the season, or null (drives the list default). */
    LocalDate selectLatestDay(@Param("seasonKey") String seasonKey);

    /**
     * The capped, ranked membership for a season: goods ids at {@code auto} on the season's most
     * recent scoring day, best score first, at most {@code limit}. This is the index's source of
     * truth for the multi-valued {@code seasons} field.
     */
    List<Integer> selectPublishedGoodsIds(@Param("seasonKey") String seasonKey,
                                          @Param("limit") int limit);

    /**
     * Whether an admin has vetoed this product for this season on ANY day.
     *
     * <p>Load-bearing: the upsert guard only protects the row for the SAME day, so without this
     * check a veto would silently expire the next time the scorer ran. The scorer carries the
     * veto forward by re-writing the new day's row as {@code dismissed}.
     */
    int countDismissed(@Param("seasonKey") String seasonKey, @Param("goodsId") int goodsId);

    /** Guarded status flip behind dismiss/restore; returns 0 when the CAS matches nothing. */
    int updateStatus(@Param("seasonKey") String seasonKey,
                     @Param("goodsId") int goodsId,
                     @Param("day") LocalDate day,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);
}
