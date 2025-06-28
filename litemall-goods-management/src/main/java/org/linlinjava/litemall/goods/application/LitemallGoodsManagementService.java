package org.linlinjava.litemall.goods.application;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.util.dto.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;

import java.util.List;
import java.util.Map;

public interface LitemallGoodsManagementService {

    void verifyGoodsAvailability(List<LitemallGoodsId> productIds);
    void reduceStock(LitemallGoodsProductId goodsProductId, Short number);

    Object addAllGoods(List<GoodsAllInOne> allGoodsList);
    Object addGoods(GoodsAllInOne goodsAllInOne);


    Object goodsDetail(LitemallGoodsId goodsId);
    Object updateGoods(GoodsAllInOne goodsAllInOne);
    Object deleteGoods(LitemallGoodsAggregate goodsAggregate);


    Object getGoodsAggregateById(LitemallGoodsId goodsId);
    Object getGoodsProductAggregateByGoodsId(LitemallGoodsId goodsId);
    Object getGoodsAttributeAggregateByGoodsId(LitemallGoodsId goodsId);
    Object getGoodsSpecificationAggregateByGoodsId(LitemallGoodsId goodsId);

}
