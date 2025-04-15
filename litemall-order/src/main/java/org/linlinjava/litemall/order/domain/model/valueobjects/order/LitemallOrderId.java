package org.linlinjava.litemall.order.domain.model.valueobjects.order;

import lombok.Getter;

@Getter
public class LitemallOrderId {
    private final Integer id;

    public LitemallOrderId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }
        this.id = id;
    }
}
