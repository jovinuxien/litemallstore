package org.linlinjava.litemall.goods.application.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.linlinjava.litemall.db.dao.LitemallSearchStatMapper;
import org.linlinjava.litemall.db.domain.LitemallKeyword;
import org.linlinjava.litemall.db.service.LitemallKeywordService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchStatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wave-22 demand-driven trending refresh over the curated {@code litemall_keyword} table
 * (the {@code /srv/search/index} hot-keyword source). Ranks the top-N queries by searches
 * over the trending window ({@code litemall_search_stats.trending-*}) and:
 * <ul>
 *   <li>marks a matching EXISTING row hot (curated rows keep their identity — only
 *       {@code is_hot} is touched, never text/url/sort);</li>
 *   <li>ADDS missing demand queries as new hot rows, flagged auto via the
 *       {@link #AUTO_SORT_ORDER} sentinel band (the table has no origin column;
 *       {@code sort_order} 999 is the documented marker — admin rows keep the
 *       admin-managed default 100, and auto rows honestly sort after them);</li>
 *   <li>un-hots ONLY stale auto rows (sentinel band) that fell out of the top-N —
 *       admin-curated rows are NEVER deleted or unhotted here.</li>
 * </ul>
 * Queries that mostly return zero results are skipped — a hot keyword must not
 * deep-link customers into an empty result page.
 */
@Service
public class SearchTrendingService {

    private static final Logger log = LoggerFactory.getLogger(SearchTrendingService.class);

    /**
     * Origin marker for demand-derived (auto) keyword rows: {@code litemall_keyword} has no
     * origin column, so auto rows are written with this {@code sort_order} sentinel (curated
     * rows keep the admin default 100). Sort semantics stay honest — auto rows rank after
     * curated ones. An admin re-sorting such a row implicitly adopts it as curated.
     */
    public static final int AUTO_SORT_ORDER = 999;

    /** How many existing keyword rows the refresh scans (well above any curated set's size). */
    private static final int EXISTING_SCAN_LIMIT = 1000;

    /** Ranking headroom: fetch extra rows so min-searches/zero-result filters still fill top-N. */
    private static final int RANKING_HEADROOM = 5;

    private final LitemallSearchStatMapper statMapper;
    private final LitemallKeywordService keywordService;
    private final LitemallSearchStatsProperties properties;

    public SearchTrendingService(LitemallSearchStatMapper statMapper,
                                 LitemallKeywordService keywordService,
                                 LitemallSearchStatsProperties properties) {
        this.statMapper = statMapper;
        this.keywordService = keywordService;
        this.properties = properties;
    }

    /** Recompute the demand-derived hot set. Honest summary out; kill-switch refuses honestly. */
    public Map<String, Object> refresh() {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            log.info("search-stats trending: disabled by kill-switch — hot keywords untouched");
            summary.put("enabled", false);
            return summary;
        }
        summary.put("enabled", true);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(0, properties.getTrendingWindowDays() - 1));
        Set<String> top = topDemand(from, to);

        List<LitemallKeyword> existing =
                keywordService.querySelective(null, null, 1, EXISTING_SCAN_LIMIT, "id", "asc");
        if (existing == null) {
            existing = List.of();
        }
        // First row wins per normalized text — duplicates (curated + auto twin) resolve stably.
        Map<String, LitemallKeyword> byNormalizedText = new HashMap<>();
        for (LitemallKeyword row : existing) {
            String normalized = SearchStatRollupTask.normalizeKeyword(row.getKeyword());
            if (normalized != null) {
                byNormalizedText.putIfAbsent(normalized, row);
            }
        }

        int added = 0;
        int markedHot = 0;
        int alreadyHot = 0;
        int unhotted = 0;
        for (String keyword : top) {
            LitemallKeyword row = byNormalizedText.get(keyword);
            if (row == null) {
                LitemallKeyword auto = new LitemallKeyword();
                auto.setKeyword(keyword);
                auto.setUrl("");
                auto.setIsHot(true);
                auto.setIsDefault(false);
                auto.setSortOrder(AUTO_SORT_ORDER);
                keywordService.add(auto);
                added++;
            } else if (!Boolean.TRUE.equals(row.getIsHot())) {
                LitemallKeyword patch = new LitemallKeyword();
                patch.setId(row.getId());
                patch.setIsHot(true);
                keywordService.updateById(patch);
                markedHot++;
            } else {
                alreadyHot++;
            }
        }
        // Decay: ONLY auto rows (sentinel band) leave the hot set; curated rows never do.
        for (LitemallKeyword row : existing) {
            if (!isAuto(row) || !Boolean.TRUE.equals(row.getIsHot())) {
                continue;
            }
            String normalized = SearchStatRollupTask.normalizeKeyword(row.getKeyword());
            if (normalized == null || top.contains(normalized)) {
                continue;
            }
            LitemallKeyword patch = new LitemallKeyword();
            patch.setId(row.getId());
            patch.setIsHot(false);
            keywordService.updateById(patch);
            unhotted++;
        }
        summary.put("from", from.toString());
        summary.put("to", to.toString());
        summary.put("top", new ArrayList<>(top));
        summary.put("added", added);
        summary.put("markedHot", markedHot);
        summary.put("alreadyHot", alreadyHot);
        summary.put("unhotted", unhotted);
        log.info("search-stats trending {}..{}: top {} — {} added, {} marked hot, {} already hot, {} auto unhotted",
                from, to, top.size(), added, markedHot, alreadyHot, unhotted);
        return summary;
    }

    /** Top-N normalized demand queries: enough searches, not predominantly zero-result. */
    private Set<String> topDemand(LocalDate from, LocalDate to) {
        int top = Math.max(0, properties.getTrendingTop());
        Set<String> keywords = new LinkedHashSet<>();
        if (top == 0) {
            return keywords;
        }
        List<Map<String, Object>> rows = statMapper.selectRange(from, to, top * RANKING_HEADROOM);
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            if (keywords.size() >= top) {
                break;
            }
            String keyword = SearchStatRollupTask.normalizeKeyword(row.get("keyword"));
            long searches = asLong(row.get("searches"));
            long zeroResults = asLong(row.get("zeroResults"));
            if (keyword == null || searches < properties.getTrendingMinSearches()) {
                continue;
            }
            // A query whose results are mostly empty must not become a storefront hot link.
            if (zeroResults * 2 >= searches) {
                continue;
            }
            keywords.add(keyword);
        }
        return keywords;
    }

    private static boolean isAuto(LitemallKeyword row) {
        return row.getSortOrder() != null && row.getSortOrder() == AUTO_SORT_ORDER;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
