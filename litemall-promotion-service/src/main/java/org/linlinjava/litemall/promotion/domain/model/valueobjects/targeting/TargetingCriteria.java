package org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The audience-selection criteria of a campaign (Katsov §3.6 — product-promotion
 * campaign targeting). A customer matches when their computed segment is in
 * {@link #targetSegments} AND each present RFM score floor is met. Empty
 * {@code targetSegments} means "any segment"; null score floors mean "no gate".
 * Immutable value object.
 */
@Getter
public class TargetingCriteria {

    private final Set<CustomerSegment> targetSegments;
    private final Integer minRecencyScore;
    private final Integer minFrequencyScore;
    private final Integer minMonetaryScore;

    public TargetingCriteria(Set<CustomerSegment> targetSegments,
                             Integer minRecencyScore,
                             Integer minFrequencyScore,
                             Integer minMonetaryScore) {
        this.targetSegments = targetSegments == null || targetSegments.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(targetSegments));
        this.minRecencyScore = minRecencyScore;
        this.minFrequencyScore = minFrequencyScore;
        this.minMonetaryScore = minMonetaryScore;
    }

    /** Whether a customer with the given segment + scores satisfies this criteria. */
    public boolean matches(CustomerSegment segment, RfmScore score) {
        if (!targetSegments.isEmpty() && !targetSegments.contains(segment)) {
            return false;
        }
        if (minRecencyScore != null && score.getRecency() < minRecencyScore) {
            return false;
        }
        if (minFrequencyScore != null && score.getFrequency() < minFrequencyScore) {
            return false;
        }
        if (minMonetaryScore != null && score.getMonetary() < minMonetaryScore) {
            return false;
        }
        return true;
    }
}
