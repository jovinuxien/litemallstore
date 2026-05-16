package org.linlinjava.litemall.order.infrastructure.messaging.model;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderServiceChangeModel {

    private String type;
    private String action;
    private String orderServiceId;
    private String correlationId;

    public OrderServiceChangeModel(String type, String action, String orderServiceId, String correlationId) {
        super();
        this.type = type;
        this.action = action;
        this.orderServiceId = orderServiceId;
        this.correlationId = correlationId;
    }
}
