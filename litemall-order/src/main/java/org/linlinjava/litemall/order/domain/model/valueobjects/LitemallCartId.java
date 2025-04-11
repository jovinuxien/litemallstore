package org.linlinjava.litemall.order.domain.model.valueobjects;


import lombok.Getter;

@Getter
public class LitemallCartId {

    private final Integer id;

    public LitemallCartId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Cart ID must be a positive integer.");
        }
        this.id = id;
    }
}
