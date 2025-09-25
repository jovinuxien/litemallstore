package org.linlinjava.litemall.goods.application.util.exception.goods;

import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(String message) {
        super("Insufficient stock for this id: " + message);
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
