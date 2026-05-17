package org.linlinjava.litemall.goods.application.goods;

import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.goods.domain.model.agregates.*;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public interface LitemallGoodsManagementService {

    /**
     * desc: catalog section contract
     */
    LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId);
    List<LitemallCategoryAggregate> getFirstLevelCategories();
    List<LitemallCategoryAggregate> getSecondLevelCategories(List<Integer> ids);
    List<LitemallCategoryAggregate> queryByPid(Integer pid);
    Object getGoodsByCategoryId(LitemallCategoryId categoryId);


    /**
     *
     * @desc goods Management section
     */
    List<LitemallGoodsAggregate> goodsByNew(int offset, int limit);
    List<LitemallGoodsAggregate> goodsByHot(int offset, int limit);
    Object addAllGoods(List<GoodsAllInOne> allGoodsList);


    void addGoods(GoodsAllInOne goodsAllInOne);
    Object goodsDetail(LitemallGoodsId goodsId, ThreadPoolExecutor executor, RejectedExecutionHandler handler, ArrayBlockingQueue<Runnable> queue);
    void updateGoods(GoodsAllInOne goodsAllInOne);
    void deleteGoods(LitemallGoodsAggregate goodsAggregate);


    LitemallGoodsAggregate getGoodsAggregateById(LitemallGoodsId goodsId);
    List<LitemallGoodsProductAggregate> getGoodsProductAggregateByGoodsId(LitemallGoodsId goodsId);
    List<LitemallGoodsAttributeAggregate> getGoodsAttributeAggregateByGoodsId(LitemallGoodsId goodsId);
    List<LitemallGoodsSpecificationAggregate> getGoodsSpecificationAggregateByGoodsId(LitemallGoodsId goodsId);


    /**
     * desc: stock verification section
     */
    void verifyGoodsAvailability(List<LitemallGoodsId> productIds);
    void reduceStock(LitemallGoodsProductId goodsProductId, Short number);

}
