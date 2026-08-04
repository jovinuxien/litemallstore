package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallPostizPost;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository over the Wave-17 Postiz publish ledger ({@code litemall_postiz_post},
 * V50). The ledger is pure audit history with no invariants, so it works on the
 * db entity directly — no aggregate wrapper (contrast
 * {@link LitemallSocialPostRepository}, whose rows carry status transitions).
 */
public interface LitemallPostizPostRepository {

    /** Persist one handoff-attempt row; the generated id lands back on the entity. */
    void insert(LitemallPostizPost row);

    /** Newest-first page of non-deleted rows. */
    PostizPostPage page(int page, int limit);

    /**
     * {@code scheduled} rows for any of the goods whose UTC schedule instant is
     * after {@code sinceUtc} (future rows included) — the composer's dedup-warning set.
     */
    List<LitemallPostizPost> scheduledSince(List<Integer> goodsIds, LocalDateTime sinceUtc);

    record PostizPostPage(int total, List<LitemallPostizPost> rows) {
    }
}
