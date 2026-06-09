package org.linlinjava.litemall.goods.application;

import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.service.LitemallCartService;
import org.linlinjava.litemall.goods.application.goods.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.application.util.exceptions.goods.LitemallGoodsNotFoundException;
import org.linlinjava.litemall.goods.application.util.exceptions.goods.LitemallGoodsProductNotFoundException;
import org.linlinjava.litemall.goods.application.util.exceptions.goods.LitemallInsufficientStockException;
import org.linlinjava.litemall.goods.domain.model.aggregates.*;
import org.linlinjava.litemall.goods.domain.model.repositories.*;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.domain.events.GoodsIndexEvent;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.messaging.GoodsChangeMessage;
import org.linlinjava.litemall.goods.infrastructure.messaging.source.GoodsIndexEventPublisher;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class LitemallGoodsManagementServiceImpl  implements LitemallGoodsManagementService {


    //private final LitemallGoodsRepository goodsRepository;
    private final LitemallCatalogRepository categoryRepository;
    private final LitemallBrandRepository brandRepository;



    private final QCodeService qCodeService;
    private final LitemallCartService cartService;
    private final LitemallCatalogService catalogService;
    private final LitemallGoodsServiceApi goodsServiceApi;
    private final LitemallGoodsProperties properties;
    private final GoodsIndexEventPublisher goodsIndexEventPublisher;

 public LitemallGoodsManagementServiceImpl(LitemallGoodsRepository goodsRepository,
                                           LitemallGoodsServiceApi goodsServiceApi,
                                           LitemallCatalogRepository categoryRepository,
                                           LitemallBrandRepository brandRepository,
                                           QCodeService qCodeService,
                                           LitemallCartService cartService,
                                           LitemallCatalogService catalogService,
                                           LitemallGoodsProperties properties,
                                           GoodsIndexEventPublisher goodsIndexEventPublisher) {
     this.goodsServiceApi = goodsServiceApi;
     this.categoryRepository = categoryRepository;
     this.brandRepository = brandRepository;
     this.qCodeService = qCodeService;
     this.cartService = cartService;
     this.catalogService = catalogService;
     this.properties = properties;
     this.goodsIndexEventPublisher = goodsIndexEventPublisher;
    }

    private void publishGoodsChange(GoodsChangeMessage.Action action, Integer goodsId) {
        if (goodsId == null) return;
        GoodsIndexEvent.Action indexAction = action == GoodsChangeMessage.Action.DELETE
                ? GoodsIndexEvent.Action.DELETE
                : GoodsIndexEvent.Action.UPSERT;
        goodsIndexEventPublisher.publish(new GoodsIndexEvent(goodsId, indexAction));
    }

   @Override
    public LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId) {
        return catalogService.getCategoryById(categoryId);
    }

    @Override
    public List<LitemallCategoryAggregate> getFirstLevelCategories() {
        return catalogService.getFirstLevelCategories();
    }

    @Override
    public List<LitemallCategoryAggregate> getSecondLevelCategories(List<Integer> ids) {
        return catalogService.getSecondLevelCategories(ids);
    }

   /* @Override
    public List<LitemallCategoryAggregate> getSecondLevelCategories(List<Integer> parentId) {
        return catalogService.getSecondLevelCategories(parentId);
    }*/

    @Override
    public List<LitemallCategoryAggregate> queryByPid(Integer pid) {
        return catalogService.queryByPid(pid);
    }

    @Override
    public Object getGoodsByCategoryId(LitemallCategoryId categoryId) {
        return catalogService.getGoodsByCategoryId(categoryId);
    }

    @Override
    public List<LitemallGoodsAggregate> goodsByNew(int offset, int limit) {
        return goodsServiceApi.getGoodsByNew(offset, limit);
    }

    @Override
    public List<LitemallGoodsAggregate> goodsByHot(int offset, int limit) {
        return goodsServiceApi.getGoodsByHot(offset, limit);
    }

    @Override
    public void verifyGoodsAvailability(List<LitemallGoodsId> goodsIds) {
        List<LitemallGoodsAggregate> goodsAggregates = goodsServiceApi.getAllGoodByIds(goodsIds);
        short numberLimitInStock = properties.getStockLowThreshold();
        if(goodsAggregates.size() != goodsIds.size()){
            throw new LitemallGoodsNotFoundException("Goods not found: " + goodsIds);
        }

        List<LitemallGoodsProductAggregate> existingProductAggregate = null;
        for(LitemallGoodsId ltmGoodsId : goodsIds){
            //existingProductAggregate = goodsProductRepository.findByGoodsId(ltmGoodsId);
            existingProductAggregate = goodsServiceApi.getProductsByGoodsId(ltmGoodsId);

            if(existingProductAggregate.isEmpty()){
                throw new LitemallGoodsProductNotFoundException("Products for this goodsId is  not found: " + ltmGoodsId);
            }

            //This method makes multiple database calls which can leads to performance issues(N+1 problem).
           /* for(LitemallGoodsProductAggregate productAggregate : existingProductAggregate){
                if(!goodsProductRepository.isStockEnough(productAggregate.getGoodsProductId(), (short) numberLimitInStock )){
                    throw new LitemallInsufficientStockException("Not enough stock for goods: " + ltmGoodsId);
                }
            }*/


            // This in memory call via Stream API check is good compared to the database call.
            if(existingProductAggregate.stream().anyMatch(productAggregate -> productAggregate.isStockEnough(numberLimitInStock))){
                throw new LitemallInsufficientStockException("Not enough stock for goods: " + ltmGoodsId);
            }
        }

 }

    @Override
    public void reduceStock(LitemallGoodsProductId productId, Short number) {
       //goodsProductRepository.reduceStock(productId, number);
       //goodsProductRepository.reduceStock(productId, number);
    }

    @Override
    public Object addAllGoods(List<GoodsAllInOne> allGoodsList) {
        return null;
    }

    @Override
    public Object goodsDetail(LitemallGoodsId goodsId, ThreadPoolExecutor executor, RejectedExecutionHandler handler, ArrayBlockingQueue<Runnable> queue) {
        Map<String, Object> data =  goodsServiceApi.getGoodsDetail(goodsId, executor, handler, queue);
        return ResponseUtil.ok(data);
    }

    @Override
    public void addGoods(GoodsAllInOne goodsAllInOne) {
        goodsServiceApi.addGoods(goodsAllInOne);
        if (goodsAllInOne.getGoods() != null && goodsAllInOne.getGoods().getGoodsId() != null) {
            publishGoodsChange(GoodsChangeMessage.Action.UPSERT, goodsAllInOne.getGoods().getGoodsId().getId());
        }
    }

    @Override
    public void updateGoods(GoodsAllInOne goodsAllInOne) {
        goodsServiceApi.updateGoods(goodsAllInOne);
        if (goodsAllInOne.getGoods() != null && goodsAllInOne.getGoods().getGoodsId() != null) {
            publishGoodsChange(GoodsChangeMessage.Action.UPSERT, goodsAllInOne.getGoods().getGoodsId().getId());
        }
    }

    @Override
    public void deleteGoods(LitemallGoodsAggregate goodsAggregate) {
        goodsServiceApi.deleteGoods(goodsAggregate);
        if (goodsAggregate != null && goodsAggregate.getGoodsId() != null) {
            publishGoodsChange(GoodsChangeMessage.Action.DELETE, goodsAggregate.getGoodsId().getId());
        }
    }

    @Override
    public LitemallGoodsAggregate getGoodsAggregateById(LitemallGoodsId goodsId) {
     return goodsServiceApi.getGoodsAggregateById(goodsId);
    }

    @Override
    public List<LitemallGoodsProductAggregate> getGoodsProductAggregateByGoodsId(LitemallGoodsId goodsId) {
     return goodsServiceApi.getProductsByGoodsId(goodsId);
    }

    @Override
    public List<LitemallGoodsAttributeAggregate> getGoodsAttributeAggregateByGoodsId(LitemallGoodsId goodsId) {
        return  goodsServiceApi.getAttributeByGoodsId(goodsId);
    }

    @Override
    public List<LitemallGoodsSpecificationAggregate> getGoodsSpecificationAggregateByGoodsId(LitemallGoodsId goodsId) {

     return goodsServiceApi.getSpecificationByGoodsId(goodsId);
    }


    private Object validate(GoodsAllInOne goodsAllInOne){

       LitemallGoodsAggregate goodsAggregate = goodsAllInOne.getGoods();

       if(StringUtils.isEmpty(goodsAggregate.getGoodsName())){
           return ResponseUtil.badArgument();
       }
        if(StringUtils.isEmpty(goodsAggregate.getGoodsSn())){
            return ResponseUtil.badArgument();
        }
        LitemallCategoryId categoryId = goodsAggregate.getCategoryId();

        LitemallManufacturerId manufacturerId = goodsAggregate.getManufacturerId();
        if(manufacturerId != null && manufacturerId.getId() != 0){
            if(brandRepository.findById(manufacturerId) != null){
                ResponseUtil.badArgumentValue();
            }
        }

        List<LitemallGoodsAttributeAggregate> attributeAggregates = goodsAllInOne.getAttributes();
        for(LitemallGoodsAttributeAggregate goodsAttributeAggregate: attributeAggregates){
            if(StringUtils.isEmpty(goodsAttributeAggregate.getAttributeName())){
                ResponseUtil.badArgumentValue();
            }

            if(StringUtils.isEmpty(goodsAttributeAggregate.getAttributeValue())){
                ResponseUtil.badArgumentValue();
            }
        }

        List<LitemallGoodsSpecificationAggregate> specificationAggregates = goodsAllInOne.getSpecifications();
        for(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate: specificationAggregates){
            if(StringUtils.isEmpty(goodsSpecificationAggregate.getSpecifications())){
                ResponseUtil.badArgument();
            }
            if(StringUtils.isEmpty(goodsSpecificationAggregate.getValue())){
                ResponseUtil.badArgument();
            }
        }

        List<LitemallGoodsProductAggregate> goodsProductAggregates = goodsAllInOne.getProducts();

        for(LitemallGoodsProductAggregate goodsProductAggregate: goodsProductAggregates){
            Integer number = goodsProductAggregate.getNumber();
            if(number == null || number < 0){
                ResponseUtil.badArgument();
            }

            LitemallMoney price = goodsProductAggregate.getPrice();
            if (price == null) {
                return ResponseUtil.badArgument();
            }

            String[] productSpecifications = goodsProductAggregate.getSpecifications();
            if (productSpecifications.length != specificationAggregates.size()){
                return ResponseUtil.badArgument();
            }
        }

        return null;
    }
}
