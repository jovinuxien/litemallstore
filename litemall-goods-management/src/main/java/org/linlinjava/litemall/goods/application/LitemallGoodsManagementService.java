package org.linlinjava.litemall.goods.application;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsSpecificationAggregate;
import org.linlinjava.litemall.goods.domain.model.util.dto.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsSpecificationId;

import java.util.List;
import java.util.Map;

public interface LitemallGoodsManagementService {

    void verifyGoodsAvailability(List<LitemallGoodsId> productIds);
    void reduceStock(Map<LitemallGoodsId, Integer> productStockMap);

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
