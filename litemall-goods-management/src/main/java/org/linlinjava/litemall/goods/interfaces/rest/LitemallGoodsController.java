package org.linlinjava.litemall.goods.interfaces.rest;


import com.github.pagehelper.PageInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.core.util.ApiResponse;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.goods.application.goods.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.dto.goods.ReduceStockRequest;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.linlinjava.litemall.goods.infrastructure.messaging.source.MessageProducer;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("srv/goods")
public class LitemallGoodsController {


    @Autowired
    private LitemallGoodsServiceApi goodsServiceApi;
    @Autowired
    private LitemallCatalogService categoryServiceApi;
    @Autowired
    private LitemallGoodsManagementService goodsManagementService;
    
    @Autowired
    private MessageProducer messageProducer;

    private static final ArrayBlockingQueue<Runnable> WORK_QUEUE = new ArrayBlockingQueue<>(9);
    private static final RejectedExecutionHandler HANDLER = new ThreadPoolExecutor.CallerRunsPolicy();
    private static final ThreadPoolExecutor executorService = new ThreadPoolExecutor(9, 9, 1000, TimeUnit.MILLISECONDS, WORK_QUEUE, HANDLER);


    /**
     *
     * @return
     */
    @GetMapping("/index")
    public Object index(){

        /*Callable<List> bannerListCallable = () -> adService.queryIndex();

        Callable<List> channelListCallable = () -> categoryService.queryChannel();

        Callable<List> couponListCallable;
        if(userId == null){
            couponListCallable = () -> couponService.queryList(0, 3);
        } else {
            couponListCallable = () -> couponService.queryAvailableList(userId,0, 3);
        }*/


        Callable<List> newGoodsListCallable = () -> goodsManagementService.goodsByHot(0, SystemConfig.getNewLimit());

        Callable<List> hotGoodsListCallable = () -> goodsManagementService.goodsByNew(0, SystemConfig.getHotLimit());

        //Callable<List> brandListCallable = () -> brandService.query(0, SystemConfig.getBrandLimit());

        //Callable<List> topicListCallable = () -> topicService.queryList(0, SystemConfig.getTopicLimit());

        //团购专区
        //Callable<List> grouponListCallable = () -> grouponService.queryList(0, 5);

        //Callable<List> floorGoodsListCallable = this::getCategoryList;
        //FutureTask<List> bannerTask = new FutureTask<>(bannerListCallable);
        //FutureTask<List> channelTask = new FutureTask<>(channelListCallable);
        //FutureTask<List> couponListTask = new FutureTask<>(couponListCallable);
        FutureTask<List> newGoodsListTask = new FutureTask<>(newGoodsListCallable);
        FutureTask<List> hotGoodsListTask = new FutureTask<>(hotGoodsListCallable);
        //FutureTask<List> brandListTask = new FutureTask<>(brandListCallable);
        //FutureTask<List> topicListTask = new FutureTask<>(topicListCallable);
        //FutureTask<List> grouponListTask = new FutureTask<>(grouponListCallable);
        //FutureTask<List> floorGoodsListTask = new FutureTask<>(floorGoodsListCallable);

        /*executorService.submit(bannerTask);
        executorService.submit(channelTask);
        executorService.submit(couponListTask);*/
        executorService.submit(newGoodsListTask);
        executorService.submit(hotGoodsListTask);
        /*executorService.submit(brandListTask);
        executorService.submit(topicListTask);
        executorService.submit(grouponListTask);*/
        //executorService.submit(floorGoodsListTask);

        Map<String, Object> entity = new HashMap<>();
        try {
           /* entity.put("banner", bannerTask.get());
            entity.put("channel", channelTask.get());
            entity.put("couponList", couponListTask.get());*/
            entity.put("newGoodsList", newGoodsListTask.get());
            entity.put("hotGoodsList", hotGoodsListTask.get());
            /*entity.put("brandList", brandListTask.get());
            entity.put("topicList", topicListTask.get());
            entity.put("grouponList", grouponListTask.get());*/
            //entity.put("floorGoodsList", floorGoodsListTask.get());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseUtil.ok(entity);

    }


    /**
     *
     * @param categoryId required
     * @param brandId required
     * @param keyword
     * @param isNew
     * @param isHot
     * @param page
     * @param limit
     * @param sort
     * @param order
     * @return
     */
    @GetMapping("/list")
    public Object listGoods(
            Integer categoryId,
            Integer brandId,
            String keyword,
            Boolean isNew,
            Boolean isHot,
            // @LoginUser Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @Sort(accepts = {"add_time", "retail_price", "name"}) @RequestParam(defaultValue = "add_time") String sort,
            @Order @RequestParam(defaultValue = "desc") String order
    ) {
        LitemallCategoryId catId = categoryId != null ? new LitemallCategoryId(categoryId) : null;
        LitemallManufacturerId manufacturerId = brandId != null ? new LitemallManufacturerId(brandId) : null;
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getGoodsBySelective(catId, manufacturerId, keyword, isHot, isNew, page, limit, sort);

        List<Integer> goodsCatsId = goodsServiceApi.getCatIds(brandId, keyword, isHot, isNew);
        List<LitemallCategoryAggregate> catList = null;

        /*if(goodsCatsId!= null &&! goodsCatsId.isEmpty()){
            catList = categoryServiceApi.getSecondLevelCategories(goodsCatsId);
        }*/

        //System.out.println("the goodsList are: " + goodsList.stream().map(LitemallGoodsAggregate::getDetail).toList());
        PageInfo<LitemallGoodsAggregate> pagedList = PageInfo.of(goodsList);

        Map<String, Object> entity = new HashMap<>();
        entity.put("list", goodsList);
        entity.put("total", pagedList.getTotal());
        entity.put("page", pagedList.getPageNum());
        entity.put("limit", pagedList.getPageSize());
        entity.put("pages", pagedList.getPages());
        entity.put("filterCategoryList", catList);

        return ResponseUtil.ok(entity);
    }

    @GetMapping("by-category")
    public Object goodsByCategory(@RequestParam Integer id) {

        LitemallCategoryId categoryId = new LitemallCategoryId(id);
        // Current category
        LitemallCategoryAggregate currentCategory = categoryServiceApi.getCategoryById(categoryId);
        if(currentCategory == null){
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallGoodsAggregate> goodsByCategory = goodsServiceApi.getGoodsByCategoryId(currentCategory.getCategoryId(), 0, 80);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("currentCategory", currentCategory);
        data.put("goodsCategory", goodsByCategory);
        System.out.println("the data are: " + data);
        return ResponseUtil.ok(data);
    }

    /**
     * Product details page "Everyone is watching" recommended products
     *
     * @param id, 商品ID
     * @return Recommended products on product details page
     */
    @GetMapping("related")
    public Object related(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        LitemallGoodsAggregate goods = goodsServiceApi.getGoodsById(goodsId);
        if (goods == null) {
            return ResponseUtil.badArgumentValue();
        }

        // The current product recommendation algorithm only recommends other products of the same category.
        LitemallCategoryId cid = new LitemallCategoryId(goods.getCategoryId().getId());

        // Find six related products
        int related = 6;
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getGoodsByCategoryId(cid, 0, related);
        return ResponseUtil.okList(goodsList);
    }


    @GetMapping("/detail")
    public Object privateGoodsDetails(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.goodsDetail(goodsId, executorService, HANDLER, WORK_QUEUE);
    }


    @GetMapping("/messages")
    //public Object sendMessage(@NotNull String message) {
    public Object sendMessage(@RequestParam @NotBlank String message) {
        messageProducer.sendMessage(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.QUEUE_NAME,
                message
        );

        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return data;
    }


    @PostMapping("/stock/reduce" )
    //public Object reduceStock(@NotNull Integer id, @NotNull Short quantity) {
    public ResponseEntity<ApiResponse<Void>> reduceStock(@RequestBody ReduceStockRequest request) {
        // Validate input
        if (request.getProductId() == null || request.getNumber() == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.fail(400, "ID and quantity are required"));
        }
        // Process the request
        LitemallGoodsProductId goodsProductId = new LitemallGoodsProductId(request.getProductId().toString());
        goodsManagementService.reduceStock(goodsProductId, request.getNumber().shortValue());
        return ResponseEntity.ok(ApiResponse.success());
    }


    @GetMapping("/goodsdetail")
    public Object getGoodsDetail(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsAggregateById(goodsId);
    }

    @GetMapping("/product")
    public Object getGoodsProductByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsProductAggregateByGoodsId(goodsId);
    }

    @GetMapping("/attribute")
    public Object getGoodsAttributeByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsAttributeAggregateByGoodsId(goodsId);
    }

    @GetMapping("/specification")
    public Object getGoodsSpecificationsByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsSpecificationAggregateByGoodsId(goodsId);
    }


    @PostMapping("/batch")
    public Map<LitemallGoodsId, LitemallGoodsAggregate> batchGoods(@RequestBody Set<Integer> goodsIds) {
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getAllGoodByIds(goodsIds.stream().map(LitemallGoodsId::new).toList());
        return goodsList.stream().collect(Collectors.toMap(LitemallGoodsAggregate::getGoodsId, Function.identity()));
    }
}

