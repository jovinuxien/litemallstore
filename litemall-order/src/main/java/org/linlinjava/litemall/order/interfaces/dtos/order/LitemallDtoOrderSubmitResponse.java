package org.linlinjava.litemall.order.interfaces.dtos.order;


import com.fasterxml.jackson.annotation.JsonInclude;
import org.linlinjava.litemall.order.domain.model.commands.LitemallOrderSubmitResult;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
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


    public static  LitemallOrderSubmitResponse fromResult(LitemallOrderSubmitResult result) {

    }
}
