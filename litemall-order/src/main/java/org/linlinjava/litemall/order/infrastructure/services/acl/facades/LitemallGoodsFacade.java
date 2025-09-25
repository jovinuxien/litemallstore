package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;

public interface LitemallGoodsFacade {
    LitemallGoodsProductAggregate getGoodsProductById(LitemallGoodsId goodsId);
    boolean validateStock(LitemallGoodsProductId productId, int quantity);
    void reduceStock(LitemallGoodsProductId productId, int quantity);
}
