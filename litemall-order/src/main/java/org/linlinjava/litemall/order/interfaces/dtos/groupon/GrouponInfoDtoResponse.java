package org.linlinjava.litemall.order.interfaces.dtos.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class GrouponInfoDtoResponse {

    private final Long grouponLinkId;
    private final String shareUrl;
    private final Integer currentParticipants;
    private final Integer requiredParticipants;
    private final Integer remainingParticipants;
    private final Double completionPercentage;

    public GrouponInfoDtoResponse(Long grouponLinkId, String shareUrl,
                               Integer currentParticipants, Integer requiredParticipants) {
        this.grouponLinkId = grouponLinkId;
        this.shareUrl = shareUrl;
        this.currentParticipants = currentParticipants;
        this.requiredParticipants = requiredParticipants;
        this.remainingParticipants = requiredParticipants != null && currentParticipants != null ?
                Math.max(0, requiredParticipants - currentParticipants) : null;
        this.completionPercentage = requiredParticipants != null && currentParticipants != null && requiredParticipants > 0 ?
                (currentParticipants.doubleValue() / requiredParticipants.doubleValue()) * 100.0 : null;
    }
}
