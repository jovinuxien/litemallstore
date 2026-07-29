package org.linlinjava.litemall.goods.application.inventoryflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallProductMetricDaily;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Service activator (Fisher et al. ch5), UPDATED + VANISHED lanes (Wave 14): proposes
 * RETIREMENT (off-sale) for goods with a hard availability problem — the pid has not been
 * seen live at CJ for {@code retire-unavailable-streak-days}+ days. Soft weakness (low stock,
 * weak margin, dead engagement) is deliberately NOT proposed here: the {@link RetirementGovernor}
 * ranks and sizes those against the catalog target after each catalog run, so the daily batch
 * stays roughly the overage instead of ballooning to every quiet product.
 *
 * <p>Streak semantics: metric rows exist only on days the flow ran, so the streak is "days since
 * the last {@code available = 1} row", never "count of consecutive zero rows" (a missed night is
 * a gap, not evidence). Proposes only; never executes. An admin's approve/dismiss/executed row is
 * never clobbered (SQL guard) and never re-proposed inside the dismiss cooldown.
 */
@Component
public class RetireCandidateScorer {

    private static final Logger log = LoggerFactory.getLogger(RetireCandidateScorer.class);

    static final int STREAK_POINTS_PER_DAY = 10;
    static final int STREAK_DAYS_CAP = 30;
    static final int ZERO_ENGAGEMENT_POINTS = 25;
    static final int NO_COST_POINTS = 20;
    static final int NEGATIVE_MARGIN_POINTS = 25;
    static final int THIN_MARGIN_POINTS = 10;
    static final BigDecimal THIN_MARGIN_PCT = new BigDecimal("15");
    static final int LOW_STOCK_POINTS = 15;

    private final LitemallRetireCandidateMapper retireMapper;
    private final LitemallProductMetricDailyMapper metricMapper;
    private final LitemallCjLinkageMapper linkageMapper;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProperties goodsProperties;
    private final InventoryFlowProperties flowProperties;

    public RetireCandidateScorer(LitemallRetireCandidateMapper retireMapper,
                                 LitemallProductMetricDailyMapper metricMapper,
                                 LitemallCjLinkageMapper linkageMapper,
                                 LitemallGoodsService goodsService,
                                 LitemallGoodsProperties goodsProperties,
                                 InventoryFlowProperties flowProperties) {
        this.retireMapper = retireMapper;
        this.metricMapper = metricMapper;
        this.linkageMapper = linkageMapper;
        this.goodsService = goodsService;
        this.goodsProperties = goodsProperties;
        this.flowProperties = flowProperties;
    }

    /** Lane activator: evaluate the event's goods, swallow every failure, pass the event on. */
    public ProductFlowEvent observe(ProductFlowEvent event) {
        try {
            Integer goodsId = linkageMapper.findAnyGoodsIdByCjPid(event.pid());
            if (goodsId != null) {
                proposeIfUnavailable(goodsId);
            }
        } catch (RuntimeException ex) {
            log.warn("inventory flow: retire scoring failed for pid {}: {}", event.pid(), ex.getMessage());
        }
        return event;
    }

    /** Hard availability gate; also reused by the governor for a resolved goods id. */
    void proposeIfUnavailable(int goodsId) {
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
            return; // deleted or already off-sale — nothing to retire
        }
        List<LitemallProductMetricDaily> series =
                metricMapper.selectSeries(goodsId, flowProperties.getRetireWindowDays());
        int streak = unavailableStreakDays(series, goods, LocalDate.now());
        if (streak < flowProperties.getRetireUnavailableStreakDays()) {
            return;
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("unavailable at CJ for " + streak + " days (threshold "
                + flowProperties.getRetireUnavailableStreakDays() + ")");
        propose(goodsId, scoreOf(streak, goods, series, reasons), reasons);
    }

    /**
     * Governor entry: propose a soft-weakness candidate the ranking selected. The caller supplies
     * the ranked reasons; the score still reflects every known weakness component. Returns whether
     * a proposal actually landed (decision guards may veto).
     */
    boolean proposeWeak(int goodsId, List<String> reasons) {
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
            return false;
        }
        List<LitemallProductMetricDaily> series =
                metricMapper.selectSeries(goodsId, flowProperties.getRetireWindowDays());
        int streak = unavailableStreakDays(series, goods, LocalDate.now());
        return propose(goodsId, scoreOf(streak, goods, series, new ArrayList<>(reasons)), reasons);
    }

    private boolean propose(int goodsId, BigDecimal score, List<String> reasons) {
        LitemallRetireCandidate latest = retireMapper.selectLatestByGoods(goodsId);
        if (latest != null && !proposable(latest)) {
            return false;
        }
        LitemallRetireCandidate candidate = new LitemallRetireCandidate();
        candidate.setGoodsId(goodsId);
        candidate.setDay(LocalDate.now());
        candidate.setScore(score);
        candidate.setReasons(toJsonArray(reasons));
        candidate.setStatus(LitemallRetireCandidate.STATUS_PROPOSED);
        retireMapper.upsertProposal(candidate);
        return true;
    }

    /**
     * A goods with a PENDING decision (approved, awaiting execution) is never re-proposed; a
     * dismissed or executed one (executed only matters after an admin re-enabled the goods —
     * off-sale goods never reach here) only after the cooldown, out of respect for the decision.
     */
    private boolean proposable(LitemallRetireCandidate latest) {
        String status = latest.getStatus();
        if (LitemallRetireCandidate.STATUS_APPROVED.equals(status)) {
            return false;
        }
        if (LitemallRetireCandidate.STATUS_DISMISSED.equals(status)
                || LitemallRetireCandidate.STATUS_EXECUTED.equals(status)) {
            LocalDate cooledOff = latest.getDay()
                    .plusDays(flowProperties.getRetireDismissCooldownDays());
            return !LocalDate.now().isBefore(cooledOff);
        }
        return true; // proposed — refreshing it is exactly what the upsert guard allows
    }

    /**
     * Days since the goods was last confirmed live at CJ. No metric rows at all ⇒ 0 (no
     * evidence, no claim). Rows but none available ⇒ the window has never seen it live —
     * count from the goods' arrival as the honest lower bound, capped at the window.
     */
    static int unavailableStreakDays(List<LitemallProductMetricDaily> series,
                                     LitemallGoods goods, LocalDate today) {
        if (series == null || series.isEmpty()) {
            return 0;
        }
        LocalDate lastAvailable = null;
        LitemallProductMetricDaily latest = null;
        for (LitemallProductMetricDaily row : series) {
            if (Boolean.TRUE.equals(row.getAvailable())
                    && (lastAvailable == null || row.getDay().isAfter(lastAvailable))) {
                lastAvailable = row.getDay();
            }
            if (latest == null || row.getDay().isAfter(latest.getDay())) {
                latest = row;
            }
        }
        if (lastAvailable != null) {
            // still live as of the latest row ⇒ no streak
            if (latest != null && Boolean.TRUE.equals(latest.getAvailable())) {
                return 0;
            }
            return (int) Math.max(0, ChronoUnit.DAYS.between(lastAvailable, today));
        }
        LocalDate anchor = goods != null && goods.getAddTime() != null
                ? goods.getAddTime().toLocalDate() : series.get(0).getDay();
        return (int) Math.max(1, ChronoUnit.DAYS.between(anchor, today));
    }

    /** Composite retire score, higher = retire sooner. Deterministic and explainable. */
    private BigDecimal scoreOf(int streak, LitemallGoods goods,
                               List<LitemallProductMetricDaily> series, List<String> reasons) {
        int score = Math.min(streak, STREAK_DAYS_CAP) * STREAK_POINTS_PER_DAY;

        long views = 0;
        long sales = 0;
        Integer latestStock = null;
        LocalDate latestStockDay = null;
        for (LitemallProductMetricDaily row : series) {
            views += row.getViews() != null ? row.getViews() : 0;
            sales += row.getSalesQty() != null ? row.getSalesQty() : 0;
            if (row.getStockTotal() != null
                    && (latestStockDay == null || row.getDay().isAfter(latestStockDay))) {
                latestStock = row.getStockTotal();
                latestStockDay = row.getDay();
            }
        }
        if (!series.isEmpty() && views == 0 && sales == 0) {
            score += ZERO_ENGAGEMENT_POINTS;
            addUnique(reasons, "no views or sales in the last "
                    + flowProperties.getRetireWindowDays() + " days");
        }

        BigDecimal retail = goods.getRetailPrice();
        BigDecimal cost = goods.getCost() != null && goods.getCost().signum() > 0 ? goods.getCost() : null;
        if (cost == null) {
            score += NO_COST_POINTS;
            addUnique(reasons, "cost not captured — margin unknown");
        } else if (retail != null && retail.signum() > 0) {
            BigDecimal marginPct = retail.subtract(cost)
                    .multiply(new BigDecimal("100")).divide(retail, 2, RoundingMode.HALF_UP);
            if (marginPct.signum() <= 0) {
                score += NEGATIVE_MARGIN_POINTS;
                addUnique(reasons, "no positive margin at current retail");
            } else if (marginPct.compareTo(THIN_MARGIN_PCT) < 0) {
                score += THIN_MARGIN_POINTS;
                addUnique(reasons, "thin margin " + marginPct + "%");
            }
        }

        if (latestStock != null && latestStock <= goodsProperties.getStockLowThreshold()) {
            score += LOW_STOCK_POINTS;
            addUnique(reasons, "stock " + latestStock + " at/below low-stock threshold "
                    + goodsProperties.getStockLowThreshold());
        }
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private static void addUnique(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    /** Tiny escape-free JSON array writer (reasons are our own plain ASCII strings). */
    static String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i).replace("\"", "'")).append('"');
        }
        return sb.append(']').toString();
    }
}
