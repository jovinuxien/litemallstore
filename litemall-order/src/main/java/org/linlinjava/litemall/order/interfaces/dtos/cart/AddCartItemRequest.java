package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddCartItemRequest {
    private Integer userId;
    private Integer goodsId;
    private Integer productId;
    private Integer number;
    private String[] specifications;
    private String goodsSn;
    private String goodsName;
    private BigDecimal price;
    private String picUrl;
}
