package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsProductId;

public interface LitemallProductRepository {
    LitemallGoodsProduct findByGoodsId(LitemallGoodsId id);

    LitemallGoodsProduct findById(LitemallGoodsProductId id);

    void addGoodsProduct(LitemallGoodsProduct product);

    void deleteById(LitemallGoodsProductId id);
    void deleteByGoodsId(LitemallGoodsId id);
    int count();


    int reduceStock(LitemallGoodsProductId id, Short num);
    int addStock(LitemallGoodsProductId id, Short num);
}
