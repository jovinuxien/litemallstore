package org.linlinjava.litemall.goods.domain.model.repositories;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsSpecificationAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsAttributeId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsSpecificationId;

import java.util.List;

public interface LitemallGoodsSpecificationRepository {

    public void add(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate);
    public int updateById(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate);
    public void removeById(LitemallGoodsSpecificationId goodsSpecificationId);
    public void removeByGoodsId(LitemallGoodsId goodsId);

    public LitemallGoodsSpecificationAggregate findById(LitemallGoodsSpecificationId goodsSpecificationId);
    public List<LitemallGoodsSpecificationAggregate> findSpecificationByGoodsId(LitemallGoodsId goodsId);
}
