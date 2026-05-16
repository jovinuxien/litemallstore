package org.linlinjava.litemall.goods.infrastructure.services.api;

import org.linlinjava.litemall.goods.domain.model.agregates.*;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public interface LitemallGoodsServiceApi {

    /**
     * Api methods for Goods Management
     */
    List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId);
    List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId, Integer offset, Integer limit);
    int getGoodsOnSale();
    List<LitemallGoodsAggregate> getGoodsByBrand(LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort, String order);

    LitemallGoodsAggregate getGoodsById(LitemallGoodsId goodsId);
    List<LitemallGoodsAggregate> getAllGoodByIds(List<LitemallGoodsId> ids);

    List<LitemallGoodsAggregate> getGoodsByHot(int offset, int limit);
    List<LitemallGoodsAggregate> getGoodsByNew(int offset, int limit);
    List<LitemallGoodsAggregate> getGoodsBySelective(LitemallCategoryId catId, LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort);
    List<Integer> getCatIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew);


    Object addAllGoods(List<GoodsAllInOne> allGoodsList);
    Object addGoods(GoodsAllInOne goodsAllInOne);
    Map<String, Object> getGoodsDetail(LitemallGoodsId goodsId, ThreadPoolExecutor executor, RejectedExecutionHandler handler, ArrayBlockingQueue<Runnable> queue);
    Object updateGoods(GoodsAllInOne goodsAllInOne);
    Object deleteGoods(LitemallGoodsAggregate goodsAggregate);


    LitemallGoodsAggregate getGoodsAggregateById(LitemallGoodsId goodsId);
    List<LitemallGoodsAttributeAggregate> getAttributeByGoodsId(LitemallGoodsId goodsId);
    List<LitemallGoodsSpecificationAggregate> getSpecificationByGoodsId(LitemallGoodsId goodsId);
    List<LitemallGoodsProductAggregate> getProductsByGoodsId(LitemallGoodsId goodsId);
}
