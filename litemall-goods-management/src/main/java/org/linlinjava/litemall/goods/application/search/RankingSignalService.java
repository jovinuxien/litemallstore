package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.goods.application.comment.CommentStatsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Writes the LOCAL review aggregate ({@code review_count}, {@code rating}) onto
 * {@code litemall_goods} from {@code litemall_comment}, so createProductDocument can emit it as
 * an indexable ranking signal — the local-goods counterpart to the CJ enrichment that fills the
 * same columns from CJ productComments. {@code listed_num} stays 0 for local goods (CJ-only
 * popularity), and the multiplicative ln2p boost keeps that neutral, not penalising.
 *
 * <p>Runs incrementally on the review write-path (one goods) and in bulk via the admin
 * {@code refresh-signals} backfill (every reviewed goods).
 */
@Service
public class RankingSignalService {

    private final CommentStatsService commentStatsService;
    private final LitemallCjLinkageMapper linkageMapper;

    public RankingSignalService(CommentStatsService commentStatsService,
                                LitemallCjLinkageMapper linkageMapper) {
        this.commentStatsService = commentStatsService;
        this.linkageMapper = linkageMapper;
    }

    /** Recompute and persist one local goods' review aggregate (called after a review is posted). */
    public void refreshLocalReviewSignal(Integer goodsId) {
        if (goodsId == null) {
            return;
        }
        Map<Integer, CommentStatsService.Stats> stats = commentStatsService.batchStats(List.of(goodsId));
        CommentStatsService.Stats s = stats.get(goodsId);
        if (s == null) {
            // No reviews (e.g. the only one was deleted): reset to zero rather than leave stale.
            linkageMapper.updateGoodsRankingSignals(goodsId, null, 0, java.math.BigDecimal.ZERO, null);
        } else {
            linkageMapper.updateGoodsRankingSignals(goodsId, null, s.reviewCount(), s.star(), null);
        }
    }

    /**
     * Backfill every reviewed local goods' aggregate in one batched pass. Returns the number of
     * goods updated — used by the admin refresh-signals endpoint before a reindex.
     */
    public int backfillLocalReviewSignals() {
        List<Integer> ids = linkageMapper.selectReviewedGoodsIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Map<Integer, CommentStatsService.Stats> stats = commentStatsService.batchStats(ids);
        int updated = 0;
        for (Map.Entry<Integer, CommentStatsService.Stats> e : stats.entrySet()) {
            linkageMapper.updateGoodsRankingSignals(e.getKey(), null,
                    e.getValue().reviewCount(), e.getValue().star(), null);
            updated++;
        }
        return updated;
    }
}
