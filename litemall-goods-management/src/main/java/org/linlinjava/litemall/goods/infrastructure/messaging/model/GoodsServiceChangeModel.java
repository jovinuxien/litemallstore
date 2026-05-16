package org.linlinjava.litemall.goods.infrastructure.messaging.model;


import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;

@Getter
@Setter
public class GoodsServiceChangeModel {

    private String type;
    private String action;
    private LitemallGoodsId litemallGoodsId;
    private String correlationId;

    public GoodsServiceChangeModel(String type, String action, LitemallGoodsId litemallGoodsId, String correlationId) {
        super();
        this.type = type;
        this.action = action;
        this.litemallGoodsId = litemallGoodsId;
        this.correlationId = correlationId;
    }
}
