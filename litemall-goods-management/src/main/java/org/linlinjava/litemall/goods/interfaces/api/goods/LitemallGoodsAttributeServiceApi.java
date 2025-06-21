package org.linlinjava.litemall.goods.interfaces.api.goods;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;

import java.util.List;

public interface LitemallGoodsAttributeServiceApi {
    List<LitemallGoodsAttributeAggregate> getByGoodsId(LitemallGoodsId goodsId);
}
