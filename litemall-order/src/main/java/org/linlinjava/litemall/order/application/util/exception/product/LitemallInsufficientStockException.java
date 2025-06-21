package org.linlinjava.litemall.order.application.util.exception.product;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(LitemallGoodsProductId goodsProductId) {
        super("Insufficient stock for this id: " + goodsProductId.getId());
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
