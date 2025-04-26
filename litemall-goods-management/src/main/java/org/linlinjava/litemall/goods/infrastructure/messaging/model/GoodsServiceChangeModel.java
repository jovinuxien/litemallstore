package org.linlinjava.litemall.goods.infrastructure.messaging.model;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoodsServiceChangeModel {

    private String type;
    private String action;
    private String orderServiceId;
    private String correlationId;

    public GoodsServiceChangeModel(String type, String action, String orderServiceId, String correlationId) {
        super();
        this.type = type;
        this.action = action;
        this.orderServiceId = orderServiceId;
        this.correlationId = correlationId;
    }
}
