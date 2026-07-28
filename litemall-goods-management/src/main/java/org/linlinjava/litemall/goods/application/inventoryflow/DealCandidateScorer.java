package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.LitemallDealCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallDealCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service activator (Fisher et al. ch5), NEW_ARRIVAL channel only: scores a freshly landed
 * product into a deal-tier PROPOSAL ({@code litemall_deal_candidate}, status {@code proposed}).
 * Deals are ONLY ever created when an admin approves a proposal — never here.
 *
 * <p>Score = marginPct × ln(1 + stock), lifted up to +50% by social proof (rating up to +25%,
 * review volume up to +25%). Tiers: ≥100 hot, ≥70 featured, else watch. No proposal at all
 * without a captured cost, a positive margin and sellable stock — honesty over volume.
 * Suggested deal price = max(cost × 1.05, retail × 0.85), never below cost.
 */
@Component
public class DealCandidateScorer {

    private static final Logger log = LoggerFactory.getLogger(DealCandidateScorer.class);

    private static final BigDecimal HOT_THRESHOLD = new BigDecimal("100");
    private static final BigDecimal FEATURED_THRESHOLD = new BigDecimal("70");
    private static final BigDecimal MIN_MARGIN_OVER_COST = new BigDecimal("1.05");
    private static final BigDecimal DEAL_DISCOUNT = new BigDecimal("0.85");
    private static final int REVIEW_SATURATION = 200;

    private final LitemallDealCandidateMapper candidateMapper;
    private final LitemallGoodsProperties goodsProperties;

    public DealCandidateScorer(LitemallDealCandidateMapper candidateMapper,
                               LitemallGoodsProperties goodsProperties) {
        this.candidateMapper = candidateMapper;
        this.goodsProperties = goodsProperties;
    }

    public ProductFlowEvent score(ProductInventoryContext ctx) {
        try {
            proposeIfWorthy(ctx);
        } catch (RuntimeException ex) {
            log.warn("inventory flow: deal scoring failed for pid {}: {}",
                    ctx.event().pid(), ex.getMessage());
        }
        return ctx.event();
    }

    private void proposeIfWorthy(ProductInventoryContext ctx) {
        if (ctx.goodsId() == null || ctx.cost() == null || ctx.marginPct() == null
                || ctx.retail() == null || ctx.retail().signum() <= 0
                || ctx.marginPct().signum() <= 0
                || ctx.stockTotal() <= goodsProperties.getStockLowThreshold()) {
            return; // no cost basis / no margin / no sellable stock — nothing honest to propose
        }

        double ratingVal = ctx.rating() == null ? 0 : Math.min(ctx.rating().doubleValue(), 5.0);
        int reviews = ctx.reviewCount() == null ? 0 : Math.max(ctx.reviewCount(), 0);
        double social = 1.0
                + (ratingVal / 5.0) * 0.25
                + (Math.min(reviews, REVIEW_SATURATION) / (double) REVIEW_SATURATION) * 0.25;
        double raw = ctx.marginPct().doubleValue() * Math.log1p(ctx.stockTotal()) * social;
        BigDecimal score = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);

        String tier = score.compareTo(HOT_THRESHOLD) >= 0 ? "hot"
                : score.compareTo(FEATURED_THRESHOLD) >= 0 ? "featured" : "watch";

        BigDecimal suggested = ctx.retail().multiply(DEAL_DISCOUNT)
                .max(ctx.cost().multiply(MIN_MARGIN_OVER_COST))
                .setScale(2, RoundingMode.HALF_UP);

        List<String> reasons = new ArrayList<>();
        reasons.add("margin " + ctx.marginPct() + "%");
        reasons.add("stock " + ctx.stockTotal());
        if (ratingVal > 0) {
            reasons.add("rating " + ctx.rating() + " (" + reviews + " reviews)");
        }

        LitemallDealCandidate candidate = new LitemallDealCandidate();
        candidate.setGoodsId(ctx.goodsId());
        candidate.setDay(LocalDate.now());
        candidate.setTier(tier);
        candidate.setScore(score);
        candidate.setSuggestedDealPrice(suggested);
        candidate.setReasons(toJsonArray(reasons));
        candidate.setStatus(LitemallDealCandidate.STATUS_PROPOSED);
        candidateMapper.upsertProposal(candidate);
    }

    /** Tiny escape-free JSON array writer (reasons are our own plain ASCII strings). */
    private static String toJsonArray(List<String> values) {
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
