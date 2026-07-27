package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;
import org.linlinjava.litemall.db.domain.LitemallProductMetricDaily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service activator (Fisher et al. ch5): the idempotent daily metric upsert — one
 * {@code litemall_product_metric_daily} row per (goods, day) with price/cost/margin,
 * stock, availability and the day's engagement (views from footprints, sales from paid
 * orders). Engagement is loaded ONCE per day in bulk (the footprint table has no goods_id
 * index — per-goods lookups would full-scan thousands of times a night).
 */
@Component
public class MarginRecorder {

    private static final Logger log = LoggerFactory.getLogger(MarginRecorder.class);

    private final LitemallProductMetricDailyMapper metricMapper;
    private final InsightMapper insightMapper;
    private final InventoryContextEnricher enricher;

    private final Object engagementLock = new Object();
    private volatile LocalDate engagementDay;
    private volatile Map<Integer, Integer> dayViews = Map.of();
    private volatile Map<Integer, Integer> daySales = Map.of();

    public MarginRecorder(LitemallProductMetricDailyMapper metricMapper,
                          InsightMapper insightMapper,
                          InventoryContextEnricher enricher) {
        this.metricMapper = metricMapper;
        this.insightMapper = insightMapper;
        this.enricher = enricher;
    }

    /** Record today's metric row for an enriched context; unresolved contexts are skipped. */
    public ProductFlowEvent record(ProductInventoryContext ctx) {
        if (ctx.goodsId() == null) {
            return ctx.event();
        }
        try {
            LocalDate today = LocalDate.now();
            loadEngagement(today);
            LitemallProductMetricDaily m = new LitemallProductMetricDaily();
            m.setGoodsId(ctx.goodsId());
            m.setDay(today);
            m.setRetailPrice(ctx.retail());
            m.setCost(ctx.cost());
            m.setMarginPct(ctx.marginPct());
            m.setStockTotal(ctx.stockTotal());
            m.setAvailable(ctx.event().kind() != ProductFlowEvent.Kind.VANISHED);
            m.setViews(dayViews.getOrDefault(ctx.goodsId(), 0));
            m.setSalesQty(daySales.getOrDefault(ctx.goodsId(), 0));
            metricMapper.upsert(m);
        } catch (RuntimeException ex) {
            log.warn("inventory flow: metric upsert failed for goods {}: {}", ctx.goodsId(), ex.getMessage());
        }
        return ctx.event();
    }

    /** Re-record a goods after an out-of-band stock change (nightly deal recheck). */
    public void recordByPid(String pid) {
        record(enricher.enrich(new ProductFlowEvent(ProductFlowEvent.Kind.UPDATED, pid)));
    }

    private void loadEngagement(LocalDate day) {
        if (day.equals(engagementDay)) {
            return;
        }
        synchronized (engagementLock) {
            if (day.equals(engagementDay)) {
                return;
            }
            dayViews = toCounts(insightMapper.selectDailyViews(day));
            daySales = toCounts(insightMapper.selectDailySales(day));
            engagementDay = day;
            log.debug("inventory flow: loaded day engagement — {} viewed, {} sold goods",
                    dayViews.size(), daySales.size());
        }
    }

    private static Map<Integer, Integer> toCounts(List<Map<String, Object>> rows) {
        Map<Integer, Integer> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("goodsId");
            Object cnt = row.get("cnt");
            if (id instanceof Number n && cnt instanceof Number c) {
                out.put(n.intValue(), c.intValue());
            }
        }
        return out;
    }
}
