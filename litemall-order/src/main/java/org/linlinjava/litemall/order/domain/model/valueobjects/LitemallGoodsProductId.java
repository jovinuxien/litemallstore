package org.linlinjava.litemall.order.domain.model.valueobjects;


import lombok.Getter;

@Getter
public class LitemallGoodsProductId {

    private final Integer id;

    public LitemallGoodsProductId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Product ID must be a positive integer.");
        }
        this.id = id;
    }
}
