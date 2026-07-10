package org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting;

import lombok.Getter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;

/**
 * Campaign budget / cap (Katsov §3.6.2 — campaign budgeting). Bounds how large
 * an audience a single evaluation may assign and how much spend the campaign may
 * accumulate. Either bound may be null (uncapped). Immutable value object.
 */
@Getter
public class CampaignBudget {

    private final Integer maxAudience;
    private final LitemallMoney maxSpend;

    public CampaignBudget(Integer maxAudience, LitemallMoney maxSpend) {
        if (maxAudience != null && maxAudience < 0) {
            throw new IllegalArgumentException("maxAudience must not be negative");
        }
        this.maxAudience = maxAudience;
        this.maxSpend = maxSpend;
    }

    public static CampaignBudget uncapped() {
        return new CampaignBudget(null, null);
    }

    /** Cap a requested audience size to the configured maximum (no-op when uncapped). */
    public int capAudience(int requested) {
        if (maxAudience == null) {
            return requested;
        }
        return Math.min(requested, maxAudience);
    }

    /** Whether adding {@code amount} keeps cumulative spend within the cap (true when uncapped). */
    public boolean allowsSpend(LitemallMoney alreadySpent, LitemallMoney amount) {
        if (maxSpend == null) {
            return true;
        }
        LitemallMoney projected = (alreadySpent == null
                ? new LitemallMoney(java.math.BigDecimal.ZERO)
                : alreadySpent).add(amount);
        return projected.isLessThanOrEqualTo(maxSpend);
    }
}
