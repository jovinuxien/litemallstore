package org.linlinjava.litemall.order.domain.model.commands;

import lombok.Getter;

@Getter
public class LitemallOrderSubmitResult {

    private final Integer orderId;
    private final boolean paid;
    private final Integer grouponLinkId;

    public LitemallOrderSubmitResult(Integer orderId, boolean paid, Integer grouponLinkId) {
        this.orderId = orderId;
        this.paid = paid;
        this.grouponLinkId = grouponLinkId;
    }

}
