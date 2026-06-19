package org.linlinjava.litemall.order.domain.model.valueobjects.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;

@Getter
public class LitemallOrderId {
    private final Integer id;

    public LitemallOrderId(Integer id) {
        // placeOrder builds a transient LitemallOrderId(0) as the "not yet persisted"
        // placeholder before the DB assigns the real id. Reject only null/negative.
        if(id == null || id < 0) {
            throw new IllegalArgumentException("Order ID must not be null or negative.");
        }
        this.id = id;
    }
}
