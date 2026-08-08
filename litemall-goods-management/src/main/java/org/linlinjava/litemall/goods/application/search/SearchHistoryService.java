package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.linlinjava.litemall.db.service.LitemallSearchHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Module-level wrapper around the litemall-db search-history table: records what an authenticated
 * customer searched for (feeding the {@code /srv/search/index} history rail) and clears it on
 * request. Recording is strictly best-effort — callers wrap it so a history-write failure can
 * never fail the search itself.
 */
@Service
public class SearchHistoryService {

    private final LitemallSearchHistoryService searchHistoryService;

    public SearchHistoryService(LitemallSearchHistoryService searchHistoryService) {
        this.searchHistoryService = searchHistoryService;
    }

    /**
     * Records {@code keyword} for {@code userId}, skipping consecutive duplicates: if the user's
     * latest non-deleted history row (add_time desc) already holds the same trimmed keyword,
     * nothing new is written. (db querySelective matches userId by equality and filters
     * deleted=false.) Returns the row the search belongs to — freshly inserted, or the existing
     * duplicate — so the caller can attach the hit total once the search completes; {@code null}
     * when nothing was recorded (anonymous / blank query).
     */
    public LitemallSearchHistory record(Integer userId, String keyword) {
        if (userId == null || keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        List<LitemallSearchHistory> latest =
                searchHistoryService.querySelective(String.valueOf(userId), null, 1, 1, "add_time", "desc");
        if (!latest.isEmpty() && trimmed.equals(latest.get(0).getKeyword())) {
            // Duplicate-skip still returns the row: the repeat search's fresh hit total may update it.
            return latest.get(0);
        }
        LitemallSearchHistory history = new LitemallSearchHistory();
        history.setUserId(userId);
        history.setKeyword(trimmed);
        history.setFrom("srv");
        searchHistoryService.save(history);
        return history;
    }

    /**
     * Wave 22: stamps the hit total onto a row returned by {@link #record}. Null-safe on every
     * input — the row stays {@code result_count = NULL} (unknown) when the search failed or the
     * total was absent; callers wrap this best-effort like the record itself.
     */
    public void recordResultCount(LitemallSearchHistory row, Long resultCount) {
        if (row == null || row.getId() == null || resultCount == null || resultCount < 0) {
            return;
        }
        LitemallSearchHistory patch = new LitemallSearchHistory();
        patch.setId(row.getId());
        patch.setResultCount((int) Math.min(resultCount, Integer.MAX_VALUE));
        searchHistoryService.updateById(patch);
    }

    /** Logically deletes all of the user's history rows. */
    public void clear(Integer userId) {
        searchHistoryService.deleteByUid(userId);
    }
}
