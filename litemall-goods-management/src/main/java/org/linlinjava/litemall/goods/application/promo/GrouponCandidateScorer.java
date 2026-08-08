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
 * Wave-19 groupon candidate scorer. Group-buys spread through sharing, so social proof
 * carries MORE weight than in the deal/coupon formulas (rating up to +40%, review volume
 * up to +35%); no hard social gate, though — an unproven product just lands in a lower
 * tier for the admin to judge.
 *
 * <p>Suggested group price = max(cost × guardFloor, retail × grouponDiscount) — never at
 * a loss. No proposal when that floored price exceeds retail × grouponMaxPriceRatio:
 * a "group deal" saving less than ~8% is not worth a campaign.
 *
 * <p>Suggestions can only become campaigns through the EXISTING promotion admin path
 * (create = DRAFT); the Phase-3 gating decision (2026-08-08) keeps groupon promotion
 * admin-side until priced submit ships.
 */
@Component
public class GrouponCandidateScorer {

    private static final BigDecimal HOT_THRESHOLD = new BigDecimal("100");
    private static final BigDecimal FEATURED_THRESHOLD = new BigDecimal("70");
    private static final int REVIEW_SATURATION = 200;

    private final LitemallPromoCandidateProperties properties;
    private final LitemallGoodsProperties goodsProperties;

    public GrouponCandidateScorer(LitemallPromoCandidateProperties properties,
                                  LitemallGoodsProperties goodsProperties) {
        this.properties = properties;
        this.goodsProperties = goodsProperties;
    }

    /** Scores one pool row into a groupon proposal, or null when there is no honest one. */
    public LitemallPromoCandidate score(PromoScoringRow row, LocalDate day) {
        if (row.cost() == null || row.marginPct() == null || row.retail() == null
                || row.retail().signum() <= 0 || row.marginPct().signum() <= 0
                || row.stockTotal() <= goodsProperties.getStockLowThreshold()) {
            return null;
        }

        BigDecimal groupPrice = row.retail().multiply(properties.getGrouponDiscount())
                .max(row.cost().multiply(properties.getGuardFloor()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxUseful = row.retail().multiply(properties.getGrouponMaxPriceRatio());
        if (groupPrice.compareTo(maxUseful) > 0) {
            return null; // cost floor eats the group discount — no real saving to advertise
        }

        double ratingVal = row.rating() == null ? 0 : Math.min(row.rating().doubleValue(), 5.0);
        int reviews = Math.max(row.reviewCount(), 0);
        double social = 1.0
                + (ratingVal / 5.0) * 0.40
                + (Math.min(reviews, REVIEW_SATURATION) / (double) REVIEW_SATURATION) * 0.35;
        double raw = row.marginPct().doubleValue() * Math.log1p(row.stockTotal()) * social;
        BigDecimal score = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);

        String tier = score.compareTo(HOT_THRESHOLD) >= 0 ? "hot"
                : score.compareTo(FEATURED_THRESHOLD) >= 0 ? "featured" : "watch";

        List<String> reasons = new ArrayList<>();
        reasons.add("margin " + row.marginPct() + "%");
        reasons.add("stock " + row.stockTotal());
        BigDecimal savingPct = BigDecimal.ONE
                .subtract(groupPrice.divide(row.retail(), 4, RoundingMode.HALF_UP))
                .multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP);
        reasons.add("group saving " + savingPct + "%");
        if (ratingVal > 0) {
            reasons.add("rating " + row.rating() + " (" + reviews + " reviews)");
        } else {
            reasons.add("no social proof yet");
        }

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("combinationPrice", groupPrice);
        suggestion.put("originalPrice", row.retail().setScale(2, RoundingMode.HALF_UP));
        suggestion.put("requiredMembers", "hot".equals(tier) ? 3 : 2);
        suggestion.put("limitPerUser", 1);
        suggestion.put("windowDays", properties.getGrouponWindowDays());

        LitemallPromoCandidate candidate = new LitemallPromoCandidate();
        candidate.setKind(LitemallPromoCandidate.KIND_GROUPON);
        candidate.setGoodsId(row.goodsId());
        candidate.setDay(day);
        candidate.setTier(tier);
        candidate.setScore(score);
        candidate.setSuggestion(PromoJson.object(suggestion));
        candidate.setReasons(PromoJson.array(reasons));
        candidate.setStatus(LitemallPromoCandidate.STATUS_PROPOSED);
        return candidate;
    }
}
