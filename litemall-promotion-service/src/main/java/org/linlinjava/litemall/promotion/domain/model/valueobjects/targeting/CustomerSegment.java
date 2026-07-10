package org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting;

/**
 * Rule-based RFM customer segments (Katsov "Introduction to Algorithmic
 * Marketing" §3.5 — RFM/segmentation building blocks). Phase 2 derives these
 * deterministically from {@link RfmScore} via {@code SegmentationService};
 * the segment thresholds come from typed config, never magic numbers. A later
 * ML phase may replace the rule mapping behind {@code AudienceSelector} without
 * changing this vocabulary.
 */
public enum CustomerSegment {
    /** High recency, frequency and monetary — the best customers. */
    CHAMPIONS,
    /** Frequent, high-value, but not necessarily most-recent. */
    LOYAL,
    /** High monetary regardless of cadence. */
    BIG_SPENDER,
    /** Previously frequent/valuable but lapsed (low recency). */
    AT_RISK,
    /** Recent first/low-frequency buyers. */
    NEW,
    /** Everyone else — long inactive / low engagement. */
    HIBERNATING;

    public static CustomerSegment fromName(String name) {
        return CustomerSegment.valueOf(name.trim().toUpperCase());
    }
}
