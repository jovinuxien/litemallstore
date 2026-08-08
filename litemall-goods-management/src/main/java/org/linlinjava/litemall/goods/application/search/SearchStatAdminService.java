package org.linlinjava.litemall.goods.application.search;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.dao.LitemallSearchStatMapper;
import org.springframework.stereotype.Service;

/**
 * Wave-22 insight read-side over {@code litemall_search_stat_daily}: the
 * {@code GET /srv/private/admin/insight/search-stats} shape — top demand queries with CTR,
 * top zero-result queries, and window totals. Aggregate-only by contract: keywords and
 * counts, never a visitor/user id.
 */
@Service
public class SearchStatAdminService {

    /** Contract: top 50 rows per list. */
    static final int TOP_LIMIT = 50;

    private static final int MAX_WINDOW_DAYS = 90;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final LitemallSearchStatMapper statMapper;

    public SearchStatAdminService(LitemallSearchStatMapper statMapper) {
        this.statMapper = statMapper;
    }

    /** The stats window ending today. {@code days} is clamped to [1, 90]. */
    public Map<String, Object> stats(int days) {
        int window = Math.max(1, Math.min(MAX_WINDOW_DAYS, days));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(window - 1L);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("days", window);
        response.put("from", from.toString());
        response.put("to", to.toString());
        response.put("topQueries", shapeRows(statMapper.selectRange(from, to, TOP_LIMIT)));
        response.put("zeroResultQueries", shapeRows(statMapper.selectRangeZeroTop(from, to, TOP_LIMIT)));
        response.put("totals", shapeTotals(statMapper.selectRangeTotals(from, to)));
        return response;
    }

    private List<Map<String, Object>> shapeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> shaped = new ArrayList<>();
        if (rows == null) {
            return shaped;
        }
        for (Map<String, Object> row : rows) {
            long searches = asLong(row.get("searches"));
            long zeroResults = asLong(row.get("zeroResults"));
            long clicks = asLong(row.get("clicks"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("keyword", row.get("keyword"));
            item.put("searches", searches);
            item.put("zeroResults", zeroResults);
            item.put("clicks", clicks);
            item.put("ctrPct", ctrPct(clicks, searches));
            shaped.add(item);
        }
        return shaped;
    }

    private Map<String, Object> shapeTotals(Map<String, Object> totals) {
        long searches = totals == null ? 0 : asLong(totals.get("searches"));
        long zeroResults = totals == null ? 0 : asLong(totals.get("zeroResults"));
        long clicks = totals == null ? 0 : asLong(totals.get("clicks"));
        Map<String, Object> shaped = new LinkedHashMap<>();
        shaped.put("searches", searches);
        shaped.put("zeroResults", zeroResults);
        shaped.put("clicks", clicks);
        shaped.put("ctrPct", ctrPct(clicks, searches));
        return shaped;
    }

    /** clicks/searches as a 1-dp percent; null (never 0) when there are no searches to divide by. */
    private static BigDecimal ctrPct(long clicks, long searches) {
        if (searches <= 0) {
            return null;
        }
        return BigDecimal.valueOf(clicks)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(searches), 1, RoundingMode.HALF_UP);
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
