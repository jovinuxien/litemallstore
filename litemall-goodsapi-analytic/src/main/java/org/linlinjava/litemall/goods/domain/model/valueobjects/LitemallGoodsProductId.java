package org.linlinjava.litemall.goods.domain.model.valueobjects;


import lombok.Getter;

@Getter
public class LitemallGoodsProductId {

    private final String id;

    public LitemallGoodsProductId(String id) {
        if(id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Product ID must be a positive integer.");
        }
        this.id = id;
    }
}
