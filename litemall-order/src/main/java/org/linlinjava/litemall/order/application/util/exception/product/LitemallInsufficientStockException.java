package org.linlinjava.litemall.order.application.util.exception.product;

import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsProductId;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(LitemallGoodsProductId goodsProductId) {
        super("Insufficient stock for this id: " + goodsProductId.getId());
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
