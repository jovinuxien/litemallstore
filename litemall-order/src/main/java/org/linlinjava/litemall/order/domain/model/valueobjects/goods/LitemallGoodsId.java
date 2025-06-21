package org.linlinjava.litemall.order.domain.model.valueobjects.goods;

public class LitemallGoodsId {

    private final Integer id;

    public LitemallGoodsId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Cart ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
