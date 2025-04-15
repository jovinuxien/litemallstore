package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.util.List;

public interface LitemallOrderGoodsRepository {

    public void add(LitemallOrderGoodsAggregate orderGoodsAggregate);

    public LitemallOrderGoodsAggregate findById(LitemallOrderGoodsId orderGoodsId);

    public List<LitemallOrderGoodsAggregate> findByOId(LitemallOrderId orderId);

    public void updateById(LitemallOrderGoodsAggregate orderGoodsAggregate);

    public Short getComments(LitemallOrderId orderId);

    public void deleteByOrderId(LitemallOrderId orderId);

    public boolean checkExist(LitemallGoodsId goodsId);

}
