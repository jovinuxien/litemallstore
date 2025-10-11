package org.linlinjava.litemall.order.infrastructure.services.feignclients;


import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;

@FeignClient(name = "goods-service", url = "${goods.service.url}", configuration = FeignConfig.class)
public interface GoodsServiceFeignClient {

    @GetMapping(value = "/goods/goodsdetail" )
    ApiResponse<LitemallGoodsAggregate> getGoodsAggregate(@NotNull Integer goodsId);

    @RequestMapping(method = RequestMethod.GET, value = "/goods/attribute")
    ApiResponse<LitemallGoodsAttributeAggregate> getGoodsAttributeAggregate(@NotNull Integer goodsId);

    @GetMapping(value = "/goods/product")
    ApiResponse<LitemallGoodsProductAggregate> getGoodsProductAggregate(@NotNull Integer goodsId);

    @PostMapping(value = "/goods/stock/reduce")
    ApiResponse<Void> reduceStock(@RequestBody ReduceStockRequest request);

    /**
     * Batch fetch goods aggregates by their IDs
     * @param goodsIds Set of goods IDs to fetch
     * @return Map of GoodsId to GoodsAggregate
     */
    @PostMapping("/api/goods/batch")
    ApiResponse<Map<Long, LitemallGoodsAggregate>> batchGetGoodsAggregates(@RequestBody Set<Integer> goodsIds);
}
