package org.linlinjava.litemall.goods.interfaces.api.goods;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;

import java.util.List;

public interface LitemallGoodsProductServiceApi {
    List<LitemallGoodsProductAggregate> getByGoodsId(LitemallGoodsId goodsId);
}
