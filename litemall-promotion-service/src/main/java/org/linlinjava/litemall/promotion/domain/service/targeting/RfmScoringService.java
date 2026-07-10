package org.linlinjava.litemall.promotion.domain.service.targeting;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.RfmScore;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionTargetingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Computes a 1..5 {@link RfmScore} from raw {@link CustomerStatistics} using the
 * config-driven boundaries in {@link PromotionTargetingProperties} — no magic
 * numbers (Katsov §3.5, RFM building block). Recency is "lower-days-is-better";
 * frequency and monetary are "higher-is-better".
 */
@Service
public class RfmScoringService {

    private final PromotionTargetingProperties properties;

    public RfmScoringService(PromotionTargetingProperties properties) {
        this.properties = properties;
    }

    public RfmScore score(CustomerStatistics stats, LocalDateTime now) {
        int r = recencyScore(stats.recencyDays(now));
        int f = ascendingScore(BigDecimal.valueOf(stats.getOrderCount()), toBigDecimals(properties.getFrequencyBoundaries()));
        int m = ascendingScore(stats.getTotalSpend().getAmount(), properties.getMonetaryBoundaries());
        return new RfmScore(r, f, m);
    }

    /** Lower days → higher score. ≤ boundary[0] → 5; > boundary[last] → 1. */
    private int recencyScore(long days) {
        List<Long> boundaries = properties.getRecencyBoundariesDays();
        int exceeded = 0;
        for (Long boundary : boundaries) {
            if (days > boundary) {
                exceeded++;
            }
        }
        return boundaries.size() + 1 - exceeded;
    }

    /** Higher value → higher score. value ≥ boundary[i] adds one to the score. */
    private int ascendingScore(BigDecimal value, List<BigDecimal> boundaries) {
        int score = 1;
        for (BigDecimal boundary : boundaries) {
            if (value.compareTo(boundary) >= 0) {
                score++;
            }
        }
        return score;
    }

    private List<BigDecimal> toBigDecimals(List<Integer> ints) {
        return ints.stream().map(BigDecimal::valueOf).collect(java.util.stream.Collectors.toList());
    }
}
