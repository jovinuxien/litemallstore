package org.linlinjava.litemall.promotion.domain.service.targeting;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerSegment;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.RfmScore;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionTargetingProperties;
import org.springframework.stereotype.Service;

/**
 * Maps an {@link RfmScore} to a rule-based {@link CustomerSegment} (Katsov §3.5
 * — segmentation building block). Rules are evaluated in priority order using
 * the cut-offs in {@link PromotionTargetingProperties} (no magic numbers). A
 * later ML phase can replace this mapping behind {@code AudienceSelector}
 * without changing the segment vocabulary or callers.
 */
@Service
public class SegmentationService {

    private final PromotionTargetingProperties.Segments rules;

    public SegmentationService(PromotionTargetingProperties properties) {
        this.rules = properties.getSegments();
    }

    public CustomerSegment classify(RfmScore score) {
        int r = score.getRecency();
        int f = score.getFrequency();
        int m = score.getMonetary();

        // CHAMPIONS: best across the board.
        if (r >= rules.getChampionMinScore()
                && f >= rules.getChampionMinScore()
                && m >= rules.getChampionMinScore()) {
            return CustomerSegment.CHAMPIONS;
        }
        // AT_RISK: lapsed (low recency) but historically frequent — worth winning back.
        if (r <= rules.getAtRiskMaxRecency() && f >= rules.getAtRiskMinFrequency()) {
            return CustomerSegment.AT_RISK;
        }
        // LOYAL: frequent and high-value, not necessarily most-recent.
        if (f >= rules.getLoyalMinScore() && m >= rules.getLoyalMinScore()) {
            return CustomerSegment.LOYAL;
        }
        // BIG_SPENDER: high monetary regardless of cadence.
        if (m >= rules.getBigSpenderMinMonetary()) {
            return CustomerSegment.BIG_SPENDER;
        }
        // NEW: recent first/low-frequency buyer.
        if (r >= rules.getNewMinRecency() && f <= rules.getNewMaxFrequency()) {
            return CustomerSegment.NEW;
        }
        // Everyone else.
        return CustomerSegment.HIBERNATING;
    }
}
