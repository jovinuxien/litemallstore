package org.linlinjava.litemall.promotion.domain.service.targeting;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.AudienceMember;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerSegment;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.RfmScore;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.TargetingCriteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link AudienceSelector}: scores each customer's RFM, classifies a
 * segment, and keeps those matching the criteria (rules + statistics, per the
 * Phase-2 decision "rules before ML"). Marked {@code @Primary} so it is the
 * injected selector until an ML implementation is introduced.
 */
@Service
@org.springframework.context.annotation.Primary
public class RuleBasedAudienceSelector implements AudienceSelector {

    private final RfmScoringService scoringService;
    private final SegmentationService segmentationService;

    public RuleBasedAudienceSelector(RfmScoringService scoringService,
                                     SegmentationService segmentationService) {
        this.scoringService = scoringService;
        this.segmentationService = segmentationService;
    }

    @Override
    public List<AudienceMember> select(TargetingCriteria criteria,
                                       List<CustomerStatistics> population,
                                       LocalDateTime now) {
        List<AudienceMember> audience = new ArrayList<>();
        for (CustomerStatistics stats : population) {
            RfmScore score = scoringService.score(stats, now);
            CustomerSegment segment = segmentationService.classify(score);
            if (criteria.matches(segment, score)) {
                audience.add(new AudienceMember(stats.getUserId(), segment, score));
            }
        }
        return audience;
    }
}
