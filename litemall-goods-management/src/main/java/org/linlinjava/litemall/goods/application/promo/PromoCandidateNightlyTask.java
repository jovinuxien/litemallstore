package org.linlinjava.litemall.goods.application.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallPromoCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.application.insight.MarginBasisService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallPromoCandidateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wave-19 nightly promo-candidate scoring (default 04:30 — clear of the 02:00 retire /
 * 03:00 sync / 03:30 enrich / 04:15 recheck chain and before the 05:00 auto-deal tick).
 * Sweeps the costed on-sale catalog (best margins first, {@code poolLimit} cap) and
 * upserts the top {@code maxPerKind} coupon and groupon proposals for the day.
 *
 * <p>Sources folded into coupon scoring: proposed RETIREMENTS become clearance
 * candidates (deeper suggested rate); L1 roots with ≥ {@code categoryArrivalMin}
 * in-window arrivals get ONE category-scoped suggestion each (on their best-scoring
 * row, rate bounded by the whole subtree's guard max via {@link MarginBasisService}).
 * Decisions are never overwritten — the upsert only refreshes {@code proposed} rows.
 */
@Component
public class PromoCandidateNightlyTask {

    private static final Logger log = LoggerFactory.getLogger(PromoCandidateNightlyTask.class);

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RETIRE_SCAN_LIMIT = 1000;

    private final InsightMapper insightMapper;
    private final LitemallPromoCandidateMapper candidateMapper;
    private final CouponCandidateScorer couponScorer;
    private final GrouponCandidateScorer grouponScorer;
    private final MarginBasisService marginBasisService;
    private final CategoryRootResolver rootResolver;
    private final LitemallPromoCandidateProperties properties;

    public PromoCandidateNightlyTask(InsightMapper insightMapper,
                                     LitemallPromoCandidateMapper candidateMapper,
                                     CouponCandidateScorer couponScorer,
                                     GrouponCandidateScorer grouponScorer,
                                     MarginBasisService marginBasisService,
                                     CategoryRootResolver rootResolver,
                                     LitemallPromoCandidateProperties properties) {
        this.insightMapper = insightMapper;
        this.candidateMapper = candidateMapper;
        this.couponScorer = couponScorer;
        this.grouponScorer = grouponScorer;
        this.marginBasisService = marginBasisService;
        this.rootResolver = rootResolver;
        this.properties = properties;
    }

    @Scheduled(cron = "${litemall.promo-candidates.cron:0 30 4 * * *}")
    public void tick() {
        try {
            run(LocalDate.now());
        } catch (RuntimeException ex) {
            log.warn("promo candidates tick failed (next run retries): {}", ex.getMessage());
        }
    }

    /** One pass; parameterized day for tests and the manual insight trigger. Honest summary out. */
    public Map<String, Object> run(LocalDate day) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            log.info("promo candidates: disabled by kill-switch — nothing proposed");
            summary.put("enabled", false);
            return summary;
        }

        List<PromoScoringRow> pool = new ArrayList<>();
        for (Map<String, Object> raw : insightMapper.selectPromoScoringPool(properties.getPoolLimit())) {
            PromoScoringRow row = PromoScoringRow.fromMap(raw);
            if (row != null) {
                pool.add(row);
            }
        }
        Set<Integer> clearanceIds = proposedRetirements();
        Map<Integer, CouponCandidateScorer.CategoryScope> hotRoots = hotArrivalRoots(pool, day);

        // Coupon proposals: goods scope everywhere; ONE category-scoped upgrade per hot root
        // (its best-scoring row), so the panel is not flooded with same-category repeats.
        List<LitemallPromoCandidate> coupons = new ArrayList<>();
        Map<Integer, PromoScoringRow> bestRowPerHotRoot = new HashMap<>();
        for (PromoScoringRow row : pool) {
            Integer root = rootResolver.rootOf(row.categoryId());
            if (root != null && hotRoots.containsKey(root)) {
                PromoScoringRow best = bestRowPerHotRoot.get(root);
                if (best == null || compareMargin(row, best) > 0) {
                    bestRowPerHotRoot.put(root, row);
                }
            }
            LitemallPromoCandidate candidate =
                    couponScorer.score(row, day, clearanceIds.contains(row.goodsId()), null);
            if (candidate != null) {
                coupons.add(candidate);
            }
        }
        for (Map.Entry<Integer, PromoScoringRow> entry : bestRowPerHotRoot.entrySet()) {
            PromoScoringRow row = entry.getValue();
            LitemallPromoCandidate candidate = couponScorer.score(row, day,
                    clearanceIds.contains(row.goodsId()), hotRoots.get(entry.getKey()));
            if (candidate != null) {
                coupons.removeIf(c -> c.getGoodsId().equals(row.goodsId()));
                coupons.add(candidate);
            }
        }

        List<LitemallPromoCandidate> groupons = new ArrayList<>();
        for (PromoScoringRow row : pool) {
            LitemallPromoCandidate candidate = grouponScorer.score(row, day);
            if (candidate != null) {
                groupons.add(candidate);
            }
        }

        int couponCount = upsertTop(coupons);
        int grouponCount = upsertTop(groupons);
        log.info("promo candidates: pool {} — {} coupon + {} groupon proposals upserted "
                        + "({} clearance goods, {} hot arrival roots)",
                pool.size(), couponCount, grouponCount, clearanceIds.size(), hotRoots.size());
        summary.put("enabled", true);
        summary.put("pool", pool.size());
        summary.put("coupon", couponCount);
        summary.put("groupon", grouponCount);
        summary.put("clearance", clearanceIds.size());
        summary.put("hotArrivalRoots", hotRoots.size());
        return summary;
    }

    private int upsertTop(List<LitemallPromoCandidate> candidates) {
        candidates.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        int cap = Math.min(candidates.size(), properties.getMaxPerKind());
        for (int i = 0; i < cap; i++) {
            candidateMapper.upsertProposal(candidates.get(i));
        }
        return cap;
    }

    private Set<Integer> proposedRetirements() {
        Set<Integer> ids = new HashSet<>();
        for (Map<String, Object> row : insightMapper.selectRetireCandidateRows("proposed", RETIRE_SCAN_LIMIT)) {
            Object goodsId = row.get("goodsId");
            if (goodsId != null) {
                ids.add(((Number) goodsId).intValue());
            }
        }
        return ids;
    }

    /** L1 roots with enough in-window arrivals AND a category-wide guard max worth a coupon. */
    private Map<Integer, CouponCandidateScorer.CategoryScope> hotArrivalRoots(
            List<PromoScoringRow> pool, LocalDate day) {
        LocalDateTime since = day.minusDays(properties.getArrivalWindowDays()).atStartOfDay();
        Map<Integer, Integer> arrivalsPerRoot = new HashMap<>();
        for (PromoScoringRow row : pool) {
            if (row.arrivalDate() == null || row.arrivalDate().isBefore(since)) {
                continue;
            }
            Integer root = rootResolver.rootOf(row.categoryId());
            if (root != null) {
                arrivalsPerRoot.merge(root, 1, Integer::sum);
            }
        }
        Map<Integer, CouponCandidateScorer.CategoryScope> hot = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : arrivalsPerRoot.entrySet()) {
            if (entry.getValue() < properties.getCategoryArrivalMin()) {
                continue;
            }
            Integer maxRate = categoryGuardMaxRate(entry.getKey());
            if (maxRate != null && maxRate >= properties.getCouponMinRate()) {
                hot.put(entry.getKey(),
                        new CouponCandidateScorer.CategoryScope(entry.getKey(), entry.getValue(), maxRate));
            }
        }
        return hot;
    }

    /** The Wave-18 guard max rate over a root's whole subtree, or null when uncosted/unreadable. */
    private Integer categoryGuardMaxRate(int rootId) {
        try {
            Map<String, Object> basis = marginBasisService.basis(null, List.of(rootId));
            Object ratio = basis.get("maxCostRatio");
            if (!(ratio instanceof BigDecimal maxCostRatio)) {
                return null;
            }
            BigDecimal max = BigDecimal.ONE
                    .subtract(maxCostRatio.multiply(properties.getGuardFloor()))
                    .multiply(HUNDRED);
            return Math.max(0, max.setScale(0, RoundingMode.FLOOR).intValue());
        } catch (RuntimeException ex) {
            log.warn("promo candidates: margin basis for root {} failed: {}", rootId, ex.getMessage());
            return null;
        }
    }

    private static int compareMargin(PromoScoringRow a, PromoScoringRow b) {
        BigDecimal ma = a.marginPct() == null ? BigDecimal.ZERO : a.marginPct();
        BigDecimal mb = b.marginPct() == null ? BigDecimal.ZERO : b.marginPct();
        return ma.compareTo(mb);
    }
}
