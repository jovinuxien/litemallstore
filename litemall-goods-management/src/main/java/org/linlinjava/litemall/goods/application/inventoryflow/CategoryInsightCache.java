package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregator target (Fisher et al. ch7): the memoized per-L1 category rollup behind
 * {@code GET /srv/private/admin/insight/categories}, ranked by potentialProfit desc.
 * Refreshed when an inventory-flow run completes; TTL'd on-demand rebuild as fallback
 * ({@code CatalogGoodsCountService}'s volatile-snapshot pattern) so the request path
 * only ever reads memory or, at worst, runs one L1-per-query rebuild.
 */
@Component
public class CategoryInsightCache {

    private static final Logger log = LoggerFactory.getLogger(CategoryInsightCache.class);

    private final InsightMapper insightMapper;
    private final LitemallCategoryService categoryService;
    private final CatalogGoodsCountService countService;
    private final LitemallGoodsProperties goodsProperties;
    private final InventoryFlowProperties flowProperties;

    private final Object refreshLock = new Object();
    private volatile List<Map<String, Object>> snapshot = List.of();
    private volatile long builtAt = 0L;

    public CategoryInsightCache(InsightMapper insightMapper,
                                LitemallCategoryService categoryService,
                                CatalogGoodsCountService countService,
                                LitemallGoodsProperties goodsProperties,
                                InventoryFlowProperties flowProperties) {
        this.insightMapper = insightMapper;
        this.categoryService = categoryService;
        this.countService = countService;
        this.goodsProperties = goodsProperties;
        this.flowProperties = flowProperties;
    }

    /** The ranked rollup; rebuilds inline when empty or older than the TTL. */
    public List<Map<String, Object>> get() {
        List<Map<String, Object>> snap = snapshot;
        long ttlMs = flowProperties.getCacheTtlSeconds() * 1000L;
        if (!snap.isEmpty() && System.currentTimeMillis() - builtAt < ttlMs) {
            return snap;
        }
        refresh(true);
        return snapshot;
    }

    /**
     * Rebuild the rollup. {@code force=false} (per-product enrichment trickle) debounces to at
     * most one rebuild per {@code cache-min-refresh-seconds}; {@code force=true} (completed
     * catalog run, admin read of a stale cache) always rebuilds.
     */
    public void refresh(boolean force) {
        long minIntervalMs = flowProperties.getCacheMinRefreshSeconds() * 1000L;
        if (!force && System.currentTimeMillis() - builtAt < minIntervalMs) {
            return;
        }
        synchronized (refreshLock) {
            if (!force && System.currentTimeMillis() - builtAt < minIntervalMs) {
                return;
            }
            try {
                List<Map<String, Object>> fresh = build();
                snapshot = fresh;
                builtAt = System.currentTimeMillis();
                log.debug("category insight cache rebuilt: {} categories", fresh.size());
            } catch (RuntimeException ex) {
                log.warn("category insight cache rebuild failed (serving stale): {}", ex.getMessage());
            }
        }
    }

    private List<Map<String, Object>> build() {
        LocalDate today = LocalDate.now();
        int lowStock = goodsProperties.getStockLowThreshold();
        int stockCap = flowProperties.getProfitStockCap();
        List<Map<String, Object>> out = new ArrayList<>();
        for (LitemallCategory root : categoryService.queryL1()) {
            List<Integer> subtree = countService.subtreeIds(root.getId());
            Map<String, Object> agg = insightMapper.selectCategoryAgg(subtree, today, lowStock, stockCap);
            if (agg == null || asLong(agg.get("onSaleCount")) <= 0) {
                continue; // only categories with on-sale goods — every row must be a real landing
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("categoryId", root.getId());
            entry.put("name", root.getName());
            entry.put("onSaleCount", asLong(agg.get("onSaleCount")));
            entry.put("newArrivals7d", asLong(agg.get("newArrivals7d")));
            entry.put("stockUnits", asLong(agg.get("stockUnits")));
            entry.put("lowStockCount", asLong(agg.get("lowStockCount")));
            entry.put("unavailableCount", asLong(agg.get("unavailableCount")));
            entry.put("avgMarginPct", agg.get("avgMarginPct")); // null when no cost captured yet
            entry.put("potentialProfit", agg.get("potentialProfit"));
            out.add(entry);
        }
        out.sort(Comparator.comparing(e -> (BigDecimal) ((Map<String, Object>) e).get("potentialProfit"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
