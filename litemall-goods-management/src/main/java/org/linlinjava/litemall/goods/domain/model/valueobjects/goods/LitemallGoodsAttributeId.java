package org.linlinjava.litemall.goods.domain.model.valueobjects.goods;

import lombok.Getter;

@Getter
public class LitemallGoodsAttributeId {

    private final String id;

    public LitemallGoodsAttributeId(String id) {
        if(id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
