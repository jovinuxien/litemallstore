package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallKeyword;
import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.linlinjava.litemall.db.service.LitemallKeywordService;
import org.linlinjava.litemall.db.service.LitemallSearchHistoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search keyword helpers (litemall-wx-api {@code WxSearchController} parity) — the default/hot
 * keyword chrome and the keyword autocomplete. Distinct from the OCS full-text search/suggest
 * ({@link SearchService}); these are backed by the curated {@code litemall_keyword} table (managed
 * by the admin keyword controller) + the per-user search history.
 *
 * <p>Anonymous-first: {@code index} returns an empty history for visitors (userId null); the
 * authenticated clear-history action is intentionally NOT here (user-service follow-up).
 */
@Service
public class SearchKeywordService {

    private final LitemallKeywordService keywordService;
    private final LitemallSearchHistoryService searchHistoryService;

    public SearchKeywordService(LitemallKeywordService keywordService,
                                LitemallSearchHistoryService searchHistoryService) {
        this.keywordService = keywordService;
        this.searchHistoryService = searchHistoryService;
    }

    /** {defaultKeyword, hotKeywordList, historyKeywordList} — history empty when anonymous. */
    public Map<String, Object> index(Integer userId) {
        LitemallKeyword defaultKeyword = keywordService.queryDefault();
        List<LitemallKeyword> hotKeywordList = keywordService.queryHots();
        List<LitemallSearchHistory> historyList =
                userId != null ? searchHistoryService.queryByUid(userId) : new ArrayList<>(0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultKeyword", defaultKeyword);
        data.put("historyKeywordList", historyList);
        data.put("hotKeywordList", hotKeywordList);
        return data;
    }

    /** Keyword autocomplete: the matching keyword phrases for a prefix. */
    public List<String> helper(String keyword, Integer page, Integer limit) {
        List<LitemallKeyword> matches = keywordService.queryByKeyword(keyword, page, limit);
        List<String> keys = new ArrayList<>(matches.size());
        for (LitemallKeyword k : matches) {
            keys.add(k.getKeyword());
        }
        return keys;
    }
}
