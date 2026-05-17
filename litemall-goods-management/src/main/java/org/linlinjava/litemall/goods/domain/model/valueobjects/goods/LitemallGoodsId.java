package org.linlinjava.litemall.goods.domain.model.valueobjects.goods;


import lombok.Getter;

@Getter
public class LitemallGoodsId {

    private final Integer id;

    public LitemallGoodsId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }
}
