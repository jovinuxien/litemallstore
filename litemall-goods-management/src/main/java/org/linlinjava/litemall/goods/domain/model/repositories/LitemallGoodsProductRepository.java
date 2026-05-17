package org.linlinjava.litemall.goods.domain.model.repositories;


import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;

import java.util.List;
import java.util.Optional;

public interface LitemallGoodsProductRepository {


    Optional<LitemallGoodsProductAggregate> findById(LitemallGoodsProductId goodsProductId);
    List<LitemallGoodsProductAggregate> findByGoodsId(LitemallGoodsId goodsId);

    void deleteById(LitemallGoodsProductId goodsProductId);
    void deleteByGoodsId(LitemallGoodsId goodsId);

    void addGoodsProduct(LitemallGoodsProductAggregate productAggregate);
    void updateGoodsById(LitemallGoodsProductAggregate goodsProductAggregate);
    int count();

    boolean isStockEnough(LitemallGoodsProductId productId, Short number);


    int reduceStock(LitemallGoodsProductId productId, Short number);
    int addStock(LitemallGoodsProductId productId, Short number);

}

