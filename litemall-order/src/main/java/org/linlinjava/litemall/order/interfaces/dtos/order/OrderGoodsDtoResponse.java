package org.linlinjava.litemall.order.interfaces.dtos.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;

import java.math.BigDecimal;

/**
 * One line item of a customer order, shaped after the SPA {@code IOrderGoods}
 * model. {@code price} is the unit price as a plain number ({@code amount}); the
 * SPA reads it via {@code priceNum()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class OrderGoodsDtoResponse {

    private final Integer id;
    private final Integer goodsId;
    private final String goodsName;
    private final String picUrl;
    private final Integer number;
    private final BigDecimal price;
    private final String[] specifications;

    public OrderGoodsDtoResponse(Integer id, Integer goodsId, String goodsName, String picUrl,
                                 Integer number, BigDecimal price, String[] specifications) {
        this.id = id;
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.picUrl = picUrl;
        this.number = number;
        this.price = price;
        this.specifications = specifications;
    }

    public static OrderGoodsDtoResponse fromDomain(LitemallOrderGoodsAggregate g) {
        return new OrderGoodsDtoResponse(
                g.getOrderGoodsId(),
                g.getGoodsId() == null ? null : g.getGoodsId().getId(),
                g.getGoodsName(),
                g.getPicUrl(),
                g.getNumber() == null ? null : g.getNumber().intValue(),
                g.getPrice() == null ? null : g.getPrice().getAmount(),
                g.getSpecifications());
    }
}
