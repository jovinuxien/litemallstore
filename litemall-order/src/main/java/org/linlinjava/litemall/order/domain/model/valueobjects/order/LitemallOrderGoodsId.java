package org.linlinjava.litemall.order.domain.model.valueobjects.order;

public class LitemallOrderGoodsId {

    private final Integer id;

    public LitemallOrderGoodsId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive integer.");
        }
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
