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
     * nothing is written. (db querySelective matches userId by equality and filters deleted=false.)
     */
    public void record(Integer userId, String keyword) {
        if (userId == null || keyword == null || keyword.isBlank()) {
            return;
        }
        String trimmed = keyword.trim();
        List<LitemallSearchHistory> latest =
                searchHistoryService.querySelective(String.valueOf(userId), null, 1, 1, "add_time", "desc");
        if (!latest.isEmpty() && trimmed.equals(latest.get(0).getKeyword())) {
            return;
        }
        LitemallSearchHistory history = new LitemallSearchHistory();
        history.setUserId(userId);
        history.setKeyword(trimmed);
        history.setFrom("srv");
        searchHistoryService.save(history);
    }

    /** Logically deletes all of the user's history rows. */
    public void clear(Integer userId) {
        searchHistoryService.deleteByUid(userId);
    }
}
