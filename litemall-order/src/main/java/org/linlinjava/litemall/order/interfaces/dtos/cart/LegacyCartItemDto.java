package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;

import java.math.BigDecimal;

/**
 * Flat cart-line shape the customer SPA consumes (its {@code IItemCart}). The cart
 * aggregate serializes its identifiers/money as nested value objects ({@code {id:..}},
 * {@code {amount:..}}) which the SPA can't read, so the legacy cart endpoints map to
 * this flat DTO instead. {@code goodsId} is a String to match the SPA model.
 */
@Data
public class LegacyCartItemDto {
    private Integer id;
    private Integer userId;
    private String goodsId;
    private String goodsSn;
    private String goodsName;
    private Integer productId;
    private BigDecimal price;
    private Integer number;
    private String[] specifications;
    private Boolean checked;
    private String picUrl;

    public static LegacyCartItemDto from(LitemallCartAggregate c) {
        LegacyCartItemDto dto = new LegacyCartItemDto();
        if (c.getCartId() != null) {
            dto.setId(c.getCartId().getId());
        }
        if (c.getUserId() != null) {
            dto.setUserId(c.getUserId().getId());
        }
        if (c.getGoodsId() != null && c.getGoodsId().getId() != null) {
            dto.setGoodsId(String.valueOf(c.getGoodsId().getId()));
        }
        if (c.getProductId() != null) {
            dto.setProductId(c.getProductId().getId());
        }
        if (c.getPrice() != null) {
            dto.setPrice(c.getPrice().getAmount());
        }
        dto.setGoodsSn(c.getGoodsSn());
        dto.setGoodsName(c.getGoodsName());
        dto.setNumber(c.getNumber());
        dto.setSpecifications(c.getSpecifications());
        dto.setChecked(c.isChecked());
        dto.setPicUrl(c.getPicUrl());
        return dto;
    }
}
