package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;

@Data
public class UpdateCartItemRequest {
    private Integer number;
    private String[] specifications;
}
