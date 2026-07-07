package org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting;

import lombok.Getter;

import java.util.Objects;

/**
 * Recency / Frequency / Monetary score triple, each on a 1..5 scale (5 = best).
 * Computed by {@code RfmScoringService} from {@link CustomerStatistics} using
 * config-driven boundaries. Immutable value object.
 */
@Getter
public class RfmScore {

    private final int recency;
    private final int frequency;
    private final int monetary;

    public RfmScore(int recency, int frequency, int monetary) {
        this.recency = clamp(recency);
        this.frequency = clamp(frequency);
        this.monetary = clamp(monetary);
    }

    private static int clamp(int score) {
        if (score < 1) return 1;
        if (score > 5) return 5;
        return score;
    }

    /** Sum of the three scores (3..15) — a coarse overall value indicator. */
    public int total() {
        return recency + frequency + monetary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RfmScore that = (RfmScore) o;
        return recency == that.recency && frequency == that.frequency && monetary == that.monetary;
    }

    @Override
    public int hashCode() {
        return Objects.hash(recency, frequency, monetary);
    }

    @Override
    public String toString() {
        return "RfmScore{R=" + recency + ", F=" + frequency + ", M=" + monetary + "}";
    }
}
