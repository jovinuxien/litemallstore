package org.linlinjava.litemall.order.interfaces.dtos.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class LitemallDtoOrderSubmitResponse {

    private final boolean success;
    private final String status;
    private final String message;
    private final Long orderId;
    private final String orderSn;
    private final boolean paymentRequired;
    private final BigDecimal actualPrice;
    private final Long grouponLinkId;
    private final String shareUrl;
    private final Integer grouponParticipants;
    private final Integer grouponRequiredMembers;
    private final Integer remainingParticipants;
    private final Double completionPercentage;

    public LitemallDtoOrderSubmitResponse(boolean success, String status, String message, Long orderId, String orderSn, boolean paymentRequired, BigDecimal actualPrice, Long grouponLinkId, String shareUrl, Integer grouponParticipants, Integer grouponRequiredMembers, Integer remainingParticipants, Double completionPercentage) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.orderId = orderId;
        this.orderSn = orderSn;
        this.paymentRequired = paymentRequired;
        this.actualPrice = actualPrice;
        this.grouponLinkId = grouponLinkId;
        this.shareUrl = shareUrl;
        this.grouponParticipants = grouponParticipants;
        this.grouponRequiredMembers = grouponRequiredMembers;
        this.remainingParticipants = remainingParticipants;
        this.completionPercentage = completionPercentage;
    }


}
