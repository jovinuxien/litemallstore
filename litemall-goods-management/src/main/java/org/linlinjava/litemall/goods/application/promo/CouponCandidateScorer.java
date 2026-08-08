package org.linlinjava.litemall.goods.application.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallPromoCandidateProperties;
import org.springframework.stereotype.Component;

/**
 * Wave-19 coupon candidate scorer. Same honesty gates as {@code DealCandidateScorer}
 * (captured cost, positive margin, sellable stock), but the demand factor is INVERTED:
 * coupons are for high-margin goods that are NOT selling — a coupon on a bestseller
 * burns margin for nothing.
 *
 * <p>Score = marginPct × ln(1 + stock) × social × demand (slow movers boosted, proven
 * sellers damped), ×1.3 for clearance (a proposed retirement). Tiers ≥100 hot, ≥70
 * featured, else watch — the deal-pipeline thresholds, so admin intuition transfers.
 *
 * <p>The suggested percent-off is BOUNDED by the Wave-18 guard formula
 * {@code (1 − cost/retail × floor) × 100} (floored to an integer), so a suggestion
 * always SAVES when the admin creates it. No rate ≥ {@code couponMinRate} ⇒ no proposal.
 */
@Component
public class CouponCandidateScorer {

    private static final BigDecimal HOT_THRESHOLD = new BigDecimal("100");
    private static final BigDecimal FEATURED_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int REVIEW_SATURATION = 200;

    private final LitemallPromoCandidateProperties properties;
    private final LitemallGoodsProperties goodsProperties;

    public CouponCandidateScorer(LitemallPromoCandidateProperties properties,
                                 LitemallGoodsProperties goodsProperties) {
        this.properties = properties;
        this.goodsProperties = goodsProperties;
    }

    /**
     * Scores one pool row into a proposal, or null when there is nothing honest to propose.
     *
     * @param categoryScope non-null = suggest a CATEGORY-scoped coupon for this L1 root
     *                      (rate additionally bounded by the whole subtree's guard max)
     */
    public LitemallPromoCandidate score(PromoScoringRow row, LocalDate day, boolean clearance,
                                        CategoryScope categoryScope) {
        if (row.cost() == null || row.marginPct() == null || row.retail() == null
                || row.retail().signum() <= 0 || row.marginPct().signum() <= 0
                || row.stockTotal() <= goodsProperties.getStockLowThreshold()) {
            return null;
        }

        int maxRate = guardMaxRate(row.cost(), row.retail());
        if (categoryScope != null) {
            maxRate = Math.min(maxRate, categoryScope.maxRate());
        }
        int targetRate = clearance ? properties.getCouponClearanceRate()
                : properties.getCouponDefaultRate();
        int rate = Math.min(targetRate, maxRate);
        if (rate < properties.getCouponMinRate()) {
            return null; // margin too thin for a coupon worth advertising
        }

        double ratingVal = row.rating() == null ? 0 : Math.min(row.rating().doubleValue(), 5.0);
        int reviews = Math.max(row.reviewCount(), 0);
        double social = 1.0
                + (ratingVal / 5.0) * 0.25
                + (Math.min(reviews, REVIEW_SATURATION) / (double) REVIEW_SATURATION) * 0.25;
        double demand = row.salesQty() == 0 ? (row.views() > 0 ? 1.15 : 1.0)
                : row.salesQty() > 10 ? 0.85 : 1.0;
        double raw = row.marginPct().doubleValue() * Math.log1p(row.stockTotal()) * social * demand
                * (clearance ? 1.3 : 1.0);
        BigDecimal score = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);

        String tier = score.compareTo(HOT_THRESHOLD) >= 0 ? "hot"
                : score.compareTo(FEATURED_THRESHOLD) >= 0 ? "featured" : "watch";

        List<String> reasons = new ArrayList<>();
        reasons.add("margin " + row.marginPct() + "%");
        reasons.add("stock " + row.stockTotal());
        if (row.salesQty() == 0) {
            reasons.add("no sales yet (" + row.views() + " views)");
        } else {
            reasons.add("sales " + row.salesQty());
        }
        if (ratingVal > 0) {
            reasons.add("rating " + row.rating() + " (" + reviews + " reviews)");
        }
        if (clearance) {
            reasons.add("clearance: retirement proposed");
        }
        if (categoryScope != null) {
            reasons.add("hot arrival category (" + categoryScope.arrivals() + " new in "
                    + properties.getArrivalWindowDays() + "d)");
        }

        Map<String, Object> suggestion = new LinkedHashMap<>();
        if (categoryScope != null) {
            suggestion.put("scopeType", "category");
            suggestion.put("categoryId", categoryScope.rootId());
        } else {
            suggestion.put("scopeType", "goods");
            suggestion.put("goodsIds", List.of(row.goodsId()));
        }
        suggestion.put("discountType", 1);
        suggestion.put("discount", rate);
        if (categoryScope == null) {
            // one item's worth of discount bounds the exposure of a single-product coupon
            suggestion.put("discountCap", row.retail().multiply(BigDecimal.valueOf(rate))
                    .divide(HUNDRED, 2, RoundingMode.CEILING));
        }
        suggestion.put("minAmount", row.retail().setScale(2, RoundingMode.HALF_UP));
        suggestion.put("maxDiscount", maxRate);

        LitemallPromoCandidate candidate = new LitemallPromoCandidate();
        candidate.setKind(LitemallPromoCandidate.KIND_COUPON);
        candidate.setGoodsId(row.goodsId());
        candidate.setDay(day);
        candidate.setTier(tier);
        candidate.setScore(score);
        candidate.setSuggestion(PromoJson.object(suggestion));
        candidate.setReasons(PromoJson.array(reasons));
        candidate.setStatus(LitemallPromoCandidate.STATUS_PROPOSED);
        return candidate;
    }

    /** Integer floor of the Wave-18 guard's max percent rate for one product's cost basis. */
    int guardMaxRate(BigDecimal cost, BigDecimal retail) {
        BigDecimal costRatio = cost.divide(retail, 4, RoundingMode.HALF_UP);
        BigDecimal max = BigDecimal.ONE
                .subtract(costRatio.multiply(properties.getGuardFloor()))
                .multiply(HUNDRED);
        return Math.max(0, max.setScale(0, RoundingMode.FLOOR).intValue());
    }

    /** A hot arrival L1 root eligible for a category-scoped suggestion. */
    public record CategoryScope(int rootId, int arrivals, int maxRate) {
    }
}
