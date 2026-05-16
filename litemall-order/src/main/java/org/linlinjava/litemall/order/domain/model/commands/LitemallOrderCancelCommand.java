package org.linlinjava.litemall.order.domain.model.commands;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

@Getter
@Setter
public class LitemallOrderCancelCommand {


    private final LitemallOrderId orderId;
    private final LitemallUserId userId;
    private final String reason;

    public LitemallOrderCancelCommand(LitemallOrderId orderId, LitemallUserId userId, String reason) {
        this.userId = userId;
        this.reason = reason;
        if(orderId == null || orderId.getId() <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }

        if(userId == null || userId.getId() <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }
        this.orderId = orderId;
    }
}
