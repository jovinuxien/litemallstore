package org.linlinjava.litemall.order.domain.model.valueobjects.goods;

public class LitemallGoodsAttributeId {

    private final Integer id;

    public LitemallGoodsAttributeId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Cart ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
