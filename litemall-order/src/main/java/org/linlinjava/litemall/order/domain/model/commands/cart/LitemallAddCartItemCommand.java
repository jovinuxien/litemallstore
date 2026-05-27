package org.linlinjava.litemall.order.domain.model.commands.cart;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Adds an item to a customer's cart. The goods snapshot fields (name, sn,
 * picUrl, price, specifications) are passed in by the caller — typically the
 * admin SPA that already holds them — so the orchestrator does not need to
 * call out to goods-management on every add.
 */
@Getter
@Setter
public class LitemallAddCartItemCommand {
    private Integer userId;
    private Integer goodsId;
    private Integer productId;
    private Integer number;

    private String goodsName;
    private String goodsSn;
    private String picUrl;
    private BigDecimal price;
    private String[] specifications;
}
