package org.linlinjava.litemall.order.domain.model.commands;

import lombok.Data;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

@Data
public class LitemallOrderPaymentCommand {


    private final LitemallOrderId orderId;
    private final LitemallUserId userId;

    public LitemallOrderPaymentCommand(LitemallOrderId orderId, LitemallUserId userId) {
        this.userId = userId;
        if (orderId == null || orderId.getId() <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }

        if (userId == null || userId.getId() <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }
        this.orderId = orderId;
    }

}
