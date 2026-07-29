package org.linlinjava.litemall.goods.application.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallDealCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;
import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallDealCandidate;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallProductMetricDaily;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.deals.FlashDealService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.application.inventoryflow.CategoryInsightCache;
import org.linlinjava.litemall.goods.application.search.CjPricing;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service behind {@code /srv/private/admin/insight/**} (Wave 12 CONTRACT).
 * Request paths read local tables and the memoized category rollup ONLY — never CJ.
 * Money is plain decimals; margin fields are null (never 0) while cost is not captured.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);

    private static final Set<String> SORT_KEYS =
            Set.of("add_time", "retail_price", "margin_pct", "stock", "sales");
    private static final int SERIES_DAYS = 90;
    private static final int LIMIT_MAX = 100;

    /** Deal-candidate action refused: nothing proposed, or already decided (contract errno). */
    public static final int ERRNO_CANDIDATE = 653;

    private final CategoryInsightCache categoryCache;
    private final InsightMapper insightMapper;
    private final LitemallProductMetricDailyMapper metricMapper;
    private final LitemallDealCandidateMapper candidateMapper;
    private final LitemallSeckillMapper seckillMapper;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductService productService;
    private final CatalogGoodsCountService countService;
    private final FlashDealService flashDealService;
    private final CjPricing pricing;
    private final LitemallGoodsProperties goodsProperties;
    private final ObjectMapper objectMapper;

    public InsightService(CategoryInsightCache categoryCache,
                          InsightMapper insightMapper,
                          LitemallProductMetricDailyMapper metricMapper,
                          LitemallDealCandidateMapper candidateMapper,
                          LitemallSeckillMapper seckillMapper,
                          LitemallGoodsService goodsService,
                          LitemallGoodsProductService productService,
                          CatalogGoodsCountService countService,
                          FlashDealService flashDealService,
                          CjPricing pricing,
                          LitemallGoodsProperties goodsProperties,
                          ObjectMapper objectMapper) {
        this.categoryCache = categoryCache;
        this.insightMapper = insightMapper;
        this.metricMapper = metricMapper;
        this.candidateMapper = candidateMapper;
        this.seckillMapper = seckillMapper;
        this.goodsService = goodsService;
        this.productService = productService;
        this.countService = countService;
        this.flashDealService = flashDealService;
        this.pricing = pricing;
        this.goodsProperties = goodsProperties;
        this.objectMapper = objectMapper;
    }

    // ---- GET /insight/categories -------------------------------------------------------------

    public Map<String, Object> categories() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", categoryCache.get());
        return data;
    }

    // ---- GET /insight/goods/list -------------------------------------------------------------

    public Map<String, Object> goodsList(Integer categoryId, String sort, String order,
                                         int page, int limit) {
        String sortKey = sort != null && SORT_KEYS.contains(sort) ? sort : "add_time";
        String sortOrder = "asc".equalsIgnoreCase(order) ? "asc" : "desc";
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(Math.max(1, limit), LIMIT_MAX);

        List<Integer> categoryIds = categoryId != null ? countService.subtreeIds(categoryId) : null;
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = insightMapper.selectGoodsPage(
                categoryIds, today, sortKey, sortOrder, safeLimit, (safePage - 1) * safeLimit);
        long total = insightMapper.countGoods(categoryIds);

        for (Map<String, Object> row : rows) {
            row.put("cjAvailable", asNullableBoolean(row.get("cjAvailable")));
            row.put("isOnSale", asNullableBoolean(row.get("isOnSale")));
            row.put("dealStatus", dealStatus((Integer) row.get("goodsId")));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", safePage);
        data.put("limit", safeLimit);
        data.put("pages", (int) Math.ceil((double) total / safeLimit));
        return data;
    }

    // ---- GET /insight/goods/{id} ---------------------------------------------------------------

    /** Full per-goods insight, or null when the goods does not exist (controller 402s). */
    public Map<String, Object> goodsInsight(int goodsId) {
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null) {
            return null;
        }
        BigDecimal cost = captured(goods.getCost());
        BigDecimal retail = goods.getRetailPrice();
        BigDecimal marginPct = marginPct(retail, cost);

        List<Map<String, Object>> variants = new ArrayList<>();
        int stockTotal = 0;
        for (LitemallGoodsProduct sku : productService.queryByGid(goodsId)) {
            int stock = sku.getNumber() != null ? sku.getNumber() : 0;
            stockTotal += stock;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("productId", sku.getId());
            v.put("cjVid", sku.getCjVid());
            v.put("specifications", sku.getSpecifications());
            v.put("price", sku.getPrice());
            v.put("cost", captured(sku.getCost()));
            v.put("stock", stock);
            v.put("available", stock > 0);
            variants.add(v);
        }

        List<Map<String, Object>> series = new ArrayList<>();
        boolean latestAvailable = true;
        for (LitemallProductMetricDaily m : metricMapper.selectSeries(goodsId, SERIES_DAYS)) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("day", m.getDay());
            s.put("retailPrice", m.getRetailPrice());
            s.put("cost", m.getCost());
            s.put("marginPct", m.getMarginPct());
            s.put("stockTotal", m.getStockTotal());
            s.put("available", Boolean.TRUE.equals(m.getAvailable()));
            s.put("views", m.getViews());
            s.put("salesQty", m.getSalesQty());
            series.add(s);
            latestAvailable = Boolean.TRUE.equals(m.getAvailable());
        }

        List<Map<String, Object>> deals = new ArrayList<>();
        for (LitemallSeckill deal : seckillMapper.selectRecentByGoodsId(goodsId)) {
            deals.add(flashDealService.toAdminView(deal));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goods", goods);
        data.put("variants", variants);
        data.put("series", series);
        data.put("totals", insightMapper.selectGoodsTotals(goodsId));
        data.put("deals", deals);
        data.put("recommendation",
                recommendation(goods, cost, retail, marginPct, stockTotal, latestAvailable));
        return data;
    }

    private Map<String, Object> recommendation(LitemallGoods goods, BigDecimal cost,
                                               BigDecimal retail, BigDecimal marginPct,
                                               int stockTotal, boolean available) {
        List<String> reasons = new ArrayList<>();
        boolean advertisable = true;
        if (cost == null) {
            advertisable = false;
            reasons.add("cost not captured yet — margin unknown");
        } else if (marginPct == null || marginPct.signum() <= 0) {
            advertisable = false;
            reasons.add("no positive margin at current retail");
        } else {
            reasons.add("margin " + marginPct + "%");
        }
        if (!Boolean.TRUE.equals(goods.getIsOnSale())) {
            advertisable = false;
            reasons.add("goods is off sale");
        }
        if (!available) {
            advertisable = false;
            reasons.add("vanished from the CJ feed");
        }
        if (stockTotal <= goodsProperties.getStockLowThreshold()) {
            advertisable = false;
            reasons.add("stock " + stockTotal + " at/below low-stock threshold "
                    + goodsProperties.getStockLowThreshold());
        } else {
            reasons.add("stock " + stockTotal);
        }
        Map<String, Object> rec = new LinkedHashMap<>();
        // null when no cost — never a fake number; margin is the category's effective one (Wave 14)
        rec.put("suggestedRetail", pricing.retailForCategory(cost, goods.getCategoryId()));
        rec.put("marginPct", marginPct);
        rec.put("advertisable", advertisable);
        rec.put("reasons", reasons);
        return rec;
    }

    // ---- GET /insight/deal-candidates ----------------------------------------------------------

    public Map<String, Object> dealCandidates(LocalDate day) {
        LocalDate effective = day != null ? day : LocalDate.now();
        List<Map<String, Object>> rows = insightMapper.selectDealCandidateRows(effective);
        for (Map<String, Object> row : rows) {
            row.put("reasons", parseReasons((String) row.get("reasons")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        return data;
    }

    // ---- POST /insight/deal-candidates/{goodsId}/approve | /dismiss ----------------------------

    /** Outcome of an approve/dismiss; {@code error != null} carries errno+errmsg. */
    public record CandidateActionResult(Integer errno, String error, Object data) {
        static CandidateActionResult fail(int errno, String error) {
            return new CandidateActionResult(errno, error, null);
        }

        static CandidateActionResult ok(Object data) {
            return new CandidateActionResult(null, null, data);
        }
    }

    public CandidateActionResult approve(int goodsId, BigDecimal dealPrice,
                                         LocalDateTime startTime, LocalDateTime stopTime,
                                         Integer stock) {
        LitemallDealCandidate candidate = candidateMapper.selectLatestByGoods(goodsId);
        if (candidate == null || !LitemallDealCandidate.STATUS_PROPOSED.equals(candidate.getStatus())) {
            return CandidateActionResult.fail(ERRNO_CANDIDATE,
                    "no proposed deal candidate for goods " + goodsId);
        }
        // The deal itself is created by the SAME author path admins use — every floor/window/
        // overlap rule (incl. the Wave-12 CJ cost floor) applies identically.
        FlashDealService.Result result =
                flashDealService.create(goodsId, dealPrice, startTime, stopTime, stock);
        if (result.error() != null) {
            return CandidateActionResult.fail(result.errno(), result.error());
        }
        int flipped = candidateMapper.updateStatus(candidate.getId(),
                LitemallDealCandidate.STATUS_PROPOSED, LitemallDealCandidate.STATUS_APPROVED);
        if (flipped == 0) {
            log.warn("insight approve: candidate {} decided concurrently — deal {} stands",
                    candidate.getId(), result.deal().getId());
        }
        return CandidateActionResult.ok(flashDealService.toAdminView(result.deal()));
    }

    public CandidateActionResult dismiss(int goodsId) {
        LitemallDealCandidate candidate = candidateMapper.selectLatestByGoods(goodsId);
        if (candidate == null || !LitemallDealCandidate.STATUS_PROPOSED.equals(candidate.getStatus())) {
            return CandidateActionResult.fail(ERRNO_CANDIDATE,
                    "no proposed deal candidate for goods " + goodsId);
        }
        int flipped = candidateMapper.updateStatus(candidate.getId(),
                LitemallDealCandidate.STATUS_PROPOSED, LitemallDealCandidate.STATUS_DISMISSED);
        if (flipped == 0) {
            return CandidateActionResult.fail(ERRNO_CANDIDATE,
                    "candidate for goods " + goodsId + " was already decided");
        }
        return CandidateActionResult.ok(Map.of("goodsId", goodsId, "status",
                LitemallDealCandidate.STATUS_DISMISSED));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String dealStatus(Integer goodsId) {
        if (goodsId == null) {
            return null;
        }
        if (seckillMapper.selectLiveByGoodsId(goodsId) != null) {
            return "live";
        }
        LitemallDealCandidate latest = candidateMapper.selectLatestByGoods(goodsId);
        return latest != null ? latest.getStatus() : null;
    }

    private List<String> parseReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (Exception ex) {
            return List.of(json);
        }
    }

    /** The V2 column default 0.00 means "not captured" — normalize to null, never a fake cost. */
    private static BigDecimal captured(BigDecimal cost) {
        return cost != null && cost.signum() > 0 ? cost : null;
    }

    private static BigDecimal marginPct(BigDecimal retail, BigDecimal cost) {
        if (retail == null || retail.signum() <= 0 || cost == null) {
            return null;
        }
        return retail.subtract(cost).multiply(new BigDecimal("100"))
                .divide(retail, 2, RoundingMode.HALF_UP);
    }

    private static Boolean asNullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return value instanceof Number n ? n.intValue() != 0 : null;
    }
}
