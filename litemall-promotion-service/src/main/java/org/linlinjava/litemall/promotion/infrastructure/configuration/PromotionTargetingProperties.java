package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Typed, environment-overridable thresholds for the Phase-2 targeting engine —
 * the single home for every RFM/segmentation tuning value so no magic numbers
 * live in the scoring/segmentation logic (Katsov §3.5). Bound from
 * {@code litemall.promotion.targeting.*}; the defaults below are the documented
 * dev baseline and may be overridden per profile via the config server.
 *
 * <p><b>Score boundaries</b> are ascending lists of length 4, dividing a metric
 * into five 1..5 buckets. Frequency/monetary are "higher is better" (value at or
 * above the i-th boundary scores i+1); recency is "lower is better" (fewer days
 * since last order scores higher).
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.targeting")
@Getter
@Setter
public class PromotionTargetingProperties {

    /** Recency buckets, in days-since-last-order (ascending). ≤30d → R5 … >240d → R1. */
    private List<Long> recencyBoundariesDays = Arrays.asList(30L, 60L, 120L, 240L);

    /** Frequency buckets, in order count (ascending). ≥12 → F5 … <2 → F1. */
    private List<Integer> frequencyBoundaries = Arrays.asList(2, 4, 7, 12);

    /** Monetary buckets, in total spend (ascending). ≥5000 → M5 … <100 → M1. */
    private List<BigDecimal> monetaryBoundaries = Arrays.asList(
            new BigDecimal("100"), new BigDecimal("500"),
            new BigDecimal("1000"), new BigDecimal("5000"));

    /** Lookback window (days) for fetching customer statistics during evaluation. */
    private long statsLookbackDays = 365L;

    /** Segment rule cut-offs (on the 1..5 RFM scale). */
    private Segments segments = new Segments();

    @Getter
    @Setter
    public static class Segments {
        /** CHAMPIONS require R,F,M all ≥ this. */
        private int championMinScore = 4;
        /** LOYAL require F and M both ≥ this. */
        private int loyalMinScore = 3;
        /** BIG_SPENDER require M ≥ this (regardless of cadence). */
        private int bigSpenderMinMonetary = 4;
        /** AT_RISK: recency ≤ this (lapsed) but historically frequent. */
        private int atRiskMaxRecency = 2;
        /** AT_RISK also require F ≥ this. */
        private int atRiskMinFrequency = 3;
        /** NEW: high recency (≥ this) ... */
        private int newMinRecency = 4;
        /** ... and low frequency (≤ this). */
        private int newMaxFrequency = 1;
    }
}
