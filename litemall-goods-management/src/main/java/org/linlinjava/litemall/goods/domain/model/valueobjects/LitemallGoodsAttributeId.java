package org.linlinjava.litemall.goods.domain.model.valueobjects;

public class LitemallGoodsAttributeId {

    private final Integer id;

    public LitemallGoodsAttributeId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
