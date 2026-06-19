package org.linlinjava.litemall.order.domain.model.valueobjects;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

@Getter
public class LitemallCartId {

    private final Integer id;

    public LitemallCartId(Integer id) {
        // 0 is the "whole checked cart" sentinel at checkout (placeOrder passes
        // cartId 0 to mean "all checked items", and clearCart treats 0 the same).
        // Reject only null/negative.
        if(id == null || id < 0) {
            throw new IllegalArgumentException("Cart ID must not be null or negative.");
        }
        this.id = id;
    }
}
