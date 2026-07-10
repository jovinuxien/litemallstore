package org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting;

import lombok.Getter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

/**
 * One selected customer in a campaign's target audience, carrying the segment
 * and RFM score that qualified them (useful for downstream delivery and audit).
 */
@Getter
public class AudienceMember {

    private final LitemallUserId userId;
    private final CustomerSegment segment;
    private final RfmScore score;

    public AudienceMember(LitemallUserId userId, CustomerSegment segment, RfmScore score) {
        this.userId = userId;
        this.segment = segment;
        this.score = score;
    }
}
