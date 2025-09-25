package org.linlinjava.litemall.goods.application;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.util.dto.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;

import java.util.List;
import java.util.Map;

public interface LitemallGoodsManagementService {

    /**
     * desc: catalog section contract
     */

    LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId);
    List<LitemallCategoryAggregate> getFirstLevelCategories();
    List<LitemallCategoryAggregate> getSecondLevelCategories(Integer parentId);
    List<LitemallCategoryAggregate> queryByPid(Integer pid);
    Object getGoodsByCategoryId(LitemallCategoryId categoryId);

    /**
     * desc: stock verification section
     */
    void verifyGoodsAvailability(List<LitemallGoodsId> productIds);
    void reduceStock(LitemallGoodsProductId goodsProductId, Short number);


    /**
     *
     * @desc goods Management section
     */
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
