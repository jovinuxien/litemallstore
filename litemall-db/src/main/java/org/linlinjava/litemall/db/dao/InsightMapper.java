package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * Hand-written read-only aggregates behind {@code /srv/private/admin/insight/**} (Wave 12).
 *
 * <p>Follows the {@link LitemallCjLinkageMapper} escape-hatch precedent: queries the generated
 * {@code *Example} API cannot express (cross-table margin/stock/sales rollups with server-side
 * sorting on computed columns). Result rows are maps keyed by the camelCase column aliases.
 * Money is plain decimals; margin aliases are NULL (never 0) when cost has not been captured
 * ({@code cost <= 0}). All statements are read-only over shared tables.
 */
public interface InsightMapper {

    /**
     * One aggregate row over the ON-SALE goods of a category subtree:
     * onSaleCount, newArrivals7d, stockUnits, lowStockCount, unavailableCount,
     * avgMarginPct, potentialProfit. {@code day} selects the metric-daily row consulted
     * for availability; {@code stockCap} bounds per-goods stock in potentialProfit.
     */
    Map<String, Object> selectCategoryAgg(@Param("categoryIds") List<Integer> categoryIds,
                                          @Param("day") LocalDate day,
                                          @Param("lowStock") int lowStock,
                                          @Param("stockCap") int stockCap);

    /**
     * A page of per-goods insight rows (goodsId, name, picUrl, isOnSale, retailPrice, cost,
     * marginAmount, marginPct, stockTotal, cjAvailable, arrivalDate, salesQty). Sorting is
     * done server-side; {@code sort} / {@code order} MUST be pre-validated by the caller —
     * the XML only branches on the known keys and falls back to arrival date.
     */
    List<Map<String, Object>> selectGoodsPage(@Param("categoryIds") List<Integer> categoryIds,
                                              @Param("day") LocalDate day,
                                              @Param("sort") String sort,
                                              @Param("order") String order,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    /** Total row count matching {@link #selectGoodsPage}'s filter. */
    long countGoods(@Param("categoryIds") List<Integer> categoryIds);

    /** Lifetime totals for one goods: views, salesQty, revenue, collects, comments. */
    Map<String, Object> selectGoodsTotals(@Param("goodsId") int goodsId);

    /**
     * Deal-candidate rows for a day joined with goods context (goodsId, name, picUrl, day,
     * tier, score, cost, retailPrice, suggestedDealPrice, stockTotal, rating, status, reasons).
     */
    List<Map<String, Object>> selectDealCandidateRows(@Param("day") LocalDate day);

    /**
     * CJ variant ids to re-check inventory for: SKUs of goods with a live (price-swapped)
     * flash deal or a recent (7-day) proposed candidate. Bounds the nightly 1 req/s
     * {@code getInventory} pass to deal-relevant goods only.
     */
    List<String> selectTrackedDealVids();

    /** Per-goods footprint views for one day: rows of {goodsId, cnt}. One scan for the whole day. */
    List<Map<String, Object>> selectDailyViews(@Param("day") LocalDate day);

    /** Per-goods paid sales quantity for one day (by order pay_time): rows of {goodsId, cnt}. */
    List<Map<String, Object>> selectDailySales(@Param("day") LocalDate day);
}
