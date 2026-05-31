package org.linlinjava.litemall.order.infrastructure.services.feignclients;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "goods-service", url = "${goods.service.url}", configuration = FeignConfig.class,
        fallbackFactory = GoodsServiceFeignClientFallbackFactory.class)
public interface GoodsServiceFeignClient {

    @GetMapping(value = "/goods/goodsdetail" )
    ApiResponse<LitemallGoodsAggregate> getGoodsAggregate(@PathVariable Integer goodsId);

    @RequestMapping(method = RequestMethod.GET, value = "/goods/attribute")
    ApiResponse<LitemallGoodsAttributeAggregate> getGoodsAttributeAggregate(@PathVariable Integer goodsId);

    @GetMapping(value = "/goods/product")
    ApiResponse<LitemallGoodsProductAggregate> getGoodsProductAggregate(@PathVariable Integer goodsId);

    @PostMapping(value = "/goods/stock/reduce")
    ApiResponse<Void> reduceStock(@RequestBody ReduceStockRequest request);

    /**
     * Batch fetch goods aggregates by their IDs
     * @param goodsIds Set of goods IDs to fetch
     * @return Map of GoodsId to GoodsAggregate
     */
    @PostMapping("/goods/batch")
    ApiResponse<Map<LitemallGoodsId, LitemallGoodsAggregate>> batchGetGoodsAggregates(@RequestBody BatchGoodsRequest goodsIds);

    @PostMapping("/products/batch")
    ApiResponse<Map<LitemallGoodsProductId, LitemallGoodsProductAggregate>> batchGetGoodsProductsAggregate(@RequestBody BatchProductsRequest request);

    @PostMapping("/stock/batch-reduce")
    //ApiResponse<BatchStockReduceResult> batchReduceStock(@RequestBody List<BatchStockReduceRequest> request);
    ApiResponse<Map<Integer, Boolean>> batchReduceStock(@RequestBody List<ReduceStockRequest> request);
}
