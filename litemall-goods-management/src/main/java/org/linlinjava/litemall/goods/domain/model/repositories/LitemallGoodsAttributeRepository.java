package org.linlinjava.litemall.goods.domain.model.repositories;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsAttributeId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;

import java.util.List;

public interface LitemallGoodsAttributeRepository {

    public void save(LitemallGoodsAttributeAggregate goodsAttributeAggregate);
    public int updateById(LitemallGoodsAttributeAggregate goodsAttributeAggregate);
    public void removeById(LitemallGoodsAttributeId goodsAttributeId);
    public void removeByGoodsId(LitemallGoodsId good);

    List<LitemallGoodsAttributeAggregate> queryByGoodsId(LitemallGoodsId goodsId);
    LitemallGoodsAttributeAggregate queryById(LitemallGoodsAttributeId id);

}
