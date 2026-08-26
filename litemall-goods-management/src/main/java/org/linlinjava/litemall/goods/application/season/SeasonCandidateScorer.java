package org.linlinjava.litemall.goods.application.season;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallSeasonCandidate;

/**
 * Pure seasonal scoring: gates, curve and tier. No Spring, no I/O — everything it needs arrives as
 * arguments, which is what makes the rules testable one case at a time.
 *
 * <h2>Global gates, identical for every season</h2>
 * A product passes all of them or it is eligible nowhere. The gates are deliberately NOT tunable
 * per season: a season may decide what it wants, never whether the store can afford to sell it.
 *
 * <h2>The curve</h2>
 * {@code marginPct × ln(1 + stock) × social}, the same shape {@code DealCandidateScorer} uses, so
 * seasonal and deal proposals rank comparably. The seasonal weights multiply on top.
 *
 * <p><b>Every factor is bounded away from zero.</b> The score is multiplicative, so any factor that
 * can reach 0 zeroes the whole thing. Freshness in particular is a BONUS in {@code [1.0, max]} and
 * never a decay toward zero — a cold-start product is un-boosted, never buried.
 */
public final class SeasonCandidateScorer {

    /** The markdown a season page implies; the same 15% the deal scorer suggests. */
    public static final BigDecimal SEASON_DISCOUNT = new BigDecimal("0.85");

    /** A discounted sale must still clear cost by this much. */
    public static final BigDecimal MIN_MARGIN_OVER_COST = new BigDecimal("1.05");

    public static final BigDecimal HOT_THRESHOLD = new BigDecimal("100");
    public static final BigDecimal FEATURED_THRESHOLD = new BigDecimal("70");

    /** Reviews stop adding social proof past this count. */
    private static final int REVIEW_SATURATION = 50;

    /** Days over which the freshness bonus decays to its floor of 1.0. */
    private static final double FRESHNESS_WINDOW_DAYS = 30.0;

    private SeasonCandidateScorer() {
    }

    /** Why a product was rejected, or {@link Rejection#NONE} when it passed every gate. */
    public enum Rejection {
        NONE,
        /** Cost was never captured, so profitability is unknowable. Fails CLOSED. */
        UNCOSTED,
        /** Would not clear cost at the season's discount. */
        UNPROFITABLE,
        /** Off sale, retired, or nothing left to sell. */
        UNAVAILABLE,
        /** Outside the configured store price band, or the season's own band. */
        OUT_OF_BAND
    }

    /**
     * The global gates.
     *
     * <p>⚠ {@code marginPct == null} means cost was never captured — it is NOT zero margin. Letting
     * null pass would put products of unknown profitability on a discounted page with no way to
     * find out. It fails closed, the same call Wave 18 had to make explicit for the coupon guard.
     *
     * @param storeFloor   configured store price floor; null or non-positive disables the check
     * @param storeCeiling configured store price ceiling; null or non-positive disables the check
     */
    public static Rejection gate(LitemallGoods goods,
                                 BigDecimal cost,
                                 int stockTotal,
                                 BigDecimal storeFloor,
                                 BigDecimal storeCeiling,
                                 BigDecimal seasonPriceMin,
                                 BigDecimal seasonPriceMax) {
        if (goods == null || goods.getRetailPrice() == null) {
            return Rejection.UNAVAILABLE;
        }
        if (!Boolean.TRUE.equals(goods.getIsOnSale()) || Boolean.TRUE.equals(goods.getDeleted())) {
            return Rejection.UNAVAILABLE;
        }
        if (stockTotal <= 0) {
            return Rejection.UNAVAILABLE;
        }
        if (cost == null || cost.signum() <= 0) {
            return Rejection.UNCOSTED;
        }

        BigDecimal retail = goods.getRetailPrice();
        if (retail.multiply(SEASON_DISCOUNT)
                .compareTo(cost.multiply(MIN_MARGIN_OVER_COST)) < 0) {
            return Rejection.UNPROFITABLE;
        }

        // Read from config, never a hardcoded 5.00 — the floor is env-configurable and has already
        // been changed once in production. Match the store's own comparison (below floor = <).
        if (storeFloor != null && storeFloor.signum() > 0 && retail.compareTo(storeFloor) < 0) {
            return Rejection.OUT_OF_BAND;
        }
        if (storeCeiling != null && storeCeiling.signum() > 0 && retail.compareTo(storeCeiling) > 0) {
            return Rejection.OUT_OF_BAND;
        }
        if (seasonPriceMin != null && retail.compareTo(seasonPriceMin) < 0) {
            return Rejection.OUT_OF_BAND;
        }
        if (seasonPriceMax != null && retail.compareTo(seasonPriceMax) > 0) {
            return Rejection.OUT_OF_BAND;
        }
        return Rejection.NONE;
    }

    /**
     * The seasonal score for a product that has already passed {@link #gate}.
     *
     * @param marginPct   margin as a percentage; caller must have rejected null already
     * @param euStocked   whether the last probe measured EU stock — a BOOST, never a filter,
     *                    because {@code eu_flag=0} means "not known", not "none"
     * @param ageDays     days since the product arrived; negative is treated as brand new
     * @param weights     the season's weights
     */
    public static BigDecimal score(BigDecimal marginPct,
                                   int stockTotal,
                                   BigDecimal rating,
                                   Integer reviewCount,
                                   boolean euStocked,
                                   boolean categoryMatched,
                                   long ageDays,
                                   SeasonWeights weights) {
        double margin = marginPct == null ? 0 : marginPct.doubleValue();
        double stock = Math.log1p(Math.max(stockTotal, 0));

        double ratingVal = rating == null ? 0 : Math.min(rating.doubleValue(), 5.0);
        int reviews = reviewCount == null ? 0 : Math.max(reviewCount, 0);
        double social = 1.0
                + (ratingVal / 5.0) * 0.25
                + (Math.min(reviews, REVIEW_SATURATION) / (double) REVIEW_SATURATION) * 0.25;

        double raw = margin * stock * social * weights.demandWeight();
        if (euStocked) {
            raw *= weights.euMultiplier();
        }
        if (categoryMatched) {
            raw *= weights.categoryBoost();
        }
        raw *= freshnessBonus(ageDays, weights.freshnessBonusMax());

        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Freshness as a BONUS in {@code [1.0, max]}, decaying linearly over the arrival window.
     *
     * <p>Not a decay toward zero, and that is the whole point: the score is multiplicative, so a
     * factor reaching 0 would zero everything and bury cold-start stock permanently. The floor of
     * 1.0 is structural here rather than a tuning value someone can set wrong.
     */
    public static double freshnessBonus(long ageDays, double max) {
        double ceiling = Math.max(max, 1.0);
        if (ageDays <= 0) {
            return ceiling;
        }
        if (ageDays >= FRESHNESS_WINDOW_DAYS) {
            return 1.0;
        }
        return 1.0 + (ceiling - 1.0) * (1.0 - (ageDays / FRESHNESS_WINDOW_DAYS));
    }

    /** Tier bands, matching the deal scorer's thresholds so proposals read comparably. */
    public static String tierOf(BigDecimal score) {
        if (score == null) {
            return LitemallSeasonCandidate.TIER_WATCH;
        }
        if (score.compareTo(HOT_THRESHOLD) >= 0) {
            return LitemallSeasonCandidate.TIER_HOT;
        }
        if (score.compareTo(FEATURED_THRESHOLD) >= 0) {
            return LitemallSeasonCandidate.TIER_FEATURED;
        }
        return LitemallSeasonCandidate.TIER_WATCH;
    }

    /**
     * Whether {@code tier} reaches {@code minTier}, for the auto-publish bar.
     * Unknown tier names never publish — an unrecognised bar fails closed.
     */
    public static boolean tierReaches(String tier, String minTier) {
        int have = tierRank(tier);
        int need = tierRank(minTier);
        return have >= 0 && need >= 0 && have >= need;
    }

    private static int tierRank(String tier) {
        if (LitemallSeasonCandidate.TIER_WATCH.equals(tier)) {
            return 0;
        }
        if (LitemallSeasonCandidate.TIER_FEATURED.equals(tier)) {
            return 1;
        }
        if (LitemallSeasonCandidate.TIER_HOT.equals(tier)) {
            return 2;
        }
        return -1;
    }

    /** Human-readable reasons, stored alongside the score so an admin can see the why. */
    public static List<String> reasons(BigDecimal marginPct,
                                       int stockTotal,
                                       boolean euStocked,
                                       boolean categoryMatched,
                                       long ageDays,
                                       String matchedTerm) {
        List<String> out = new java.util.ArrayList<>();
        if (matchedTerm != null && !matchedTerm.isBlank()) {
            out.add("matches season term \"" + matchedTerm + "\"");
        }
        if (marginPct != null) {
            out.add("margin " + marginPct.setScale(1, RoundingMode.HALF_UP) + "%");
        }
        out.add("stock " + stockTotal);
        if (euStocked) {
            out.add("EU stock at last check");
        }
        if (categoryMatched) {
            out.add("in a boosted category");
        }
        if (ageDays >= 0 && ageDays < FRESHNESS_WINDOW_DAYS) {
            out.add("arrived " + ageDays + "d ago");
        }
        return out;
    }
}
