package org.linlinjava.litemall.goods.application.insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallCjSyncRunMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCjSyncRun;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.springframework.stereotype.Service;

/**
 * Wave-14 arrivals insight ({@code GET /srv/private/admin/insight/arrivals?runs=1|2}): ranks L1
 * categories by the deal quality of what ARRIVED in the last N complete catalog runs. Window
 * boundaries come from {@code litemall_cj_sync_run} phase {@code flow} rows — only complete
 * runs qualify (an aborted run is not a boundary), and the enrichment trickle writes no flow
 * rows, so these are exactly the catalog cycles. dealScore = avg best deal-candidate score of
 * the window's arrivals per root; categories with zero arrivals are omitted per the contract.
 */
@Service
public class ArrivalInsightService {

    private static final int MAX_RUNS = 2;

    private final LitemallCjSyncRunMapper syncRunMapper;
    private final InsightMapper insightMapper;
    private final LitemallCategoryService categoryService;
    private final CatalogGoodsCountService countService;

    public ArrivalInsightService(LitemallCjSyncRunMapper syncRunMapper,
                                 InsightMapper insightMapper,
                                 LitemallCategoryService categoryService,
                                 CatalogGoodsCountService countService) {
        this.syncRunMapper = syncRunMapper;
        this.insightMapper = insightMapper;
        this.categoryService = categoryService;
        this.countService = countService;
    }

    public Map<String, Object> arrivals(Integer runs) {
        int window = runs == null ? 1 : Math.max(1, Math.min(MAX_RUNS, runs));
        List<LitemallCjSyncRun> recent =
                syncRunMapper.selectRecentComplete(LitemallCjSyncRun.PHASE_FLOW, window);
        Map<String, Object> data = new LinkedHashMap<>();
        if (recent.isEmpty()) {
            data.put("since", null);
            data.put("runs", 0);
            data.put("categories", List.of());
            return data;
        }
        LocalDateTime since = recent.get(recent.size() - 1).getStartedTime();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (LitemallCategory root : categoryService.queryL1()) {
            Map<String, Object> agg = insightMapper.selectArrivalsAgg(
                    countService.subtreeIds(root.getId()), since, since.toLocalDate());
            long arrivals = agg.get("arrivals") instanceof Number n ? n.longValue() : 0;
            if (arrivals == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryId", root.getId());
            row.put("name", root.getName());
            row.put("arrivals", arrivals);
            row.put("avgMarginPct", agg.get("avgMarginPct"));
            row.put("avgRetailPrice", agg.get("avgRetailPrice"));
            row.put("dealScore", agg.get("dealScore"));
            categories.add(row);
        }
        categories.sort((a, b) -> decimal(b.get("dealScore")).compareTo(decimal(a.get("dealScore"))));
        data.put("since", since.toString());
        data.put("runs", recent.size());
        data.put("categories", categories);
        return data;
    }

    private static BigDecimal decimal(Object o) {
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        return o instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
