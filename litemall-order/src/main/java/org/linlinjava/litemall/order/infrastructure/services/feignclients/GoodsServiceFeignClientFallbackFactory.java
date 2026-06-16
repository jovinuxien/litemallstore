package org.linlinjava.litemall.order.infrastructure.services.feignclients;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.BatchGoodsRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.BatchProductsRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Circuit-breaker fallback for {@link GoodsServiceFeignClient}. When
 * goods-management is unreachable / times out / the breaker is open, every call
 * returns an error {@code ApiResponse} (errno {@value #SERVICE_UNAVAILABLE_ERRNO})
 * carrying the triggering cause. {@code FeignResponseHandler} then throws, and
 * {@code LitemallGoodsFacadeImpl} translates that into a
 * {@code LitemallGoodsServiceUnavailableException} — so a placement in flight
 * rolls back cleanly rather than hanging or proceeding on unvalidated stock.
 */
@Component
public class GoodsServiceFeignClientFallbackFactory implements FallbackFactory<GoodsServiceFeignClient> {

    static final int SERVICE_UNAVAILABLE_ERRNO = 503;

    private static final Logger log = LoggerFactory.getLogger(GoodsServiceFeignClientFallbackFactory.class);

    @Override
    public GoodsServiceFeignClient create(Throwable cause) {
        log.error("goods-service circuit fallback engaged: {}", cause.toString());
        return new GoodsServiceFeignClient() {
            @Override
            public ApiResponse<LitemallGoodsAggregate> getGoodsAggregate(Integer goodsId) {
                return error("getGoodsAggregate");
            }

            @Override
            public ApiResponse<LitemallGoodsAttributeAggregate> getGoodsAttributeAggregate(Integer goodsId) {
                return error("getGoodsAttributeAggregate");
            }

            @Override
            public ApiResponse<LitemallGoodsProductAggregate> getGoodsProductAggregate(Integer goodsId) {
                return error("getGoodsProductAggregate");
            }

            @Override
            public ApiResponse<Void> reduceStock(ReduceStockRequest request) {
                return error("reduceStock");
            }

            @Override
            public ApiResponse<Map<LitemallGoodsId, LitemallGoodsAggregate>> batchGetGoodsAggregates(BatchGoodsRequest goodsIds) {
                return error("batchGetGoodsAggregates");
            }

            @Override
            public ApiResponse<Map<LitemallGoodsProductId, LitemallGoodsProductAggregate>> batchGetGoodsProductsAggregate(BatchProductsRequest request) {
                return error("batchGetGoodsProductsAggregate");
            }

            @Override
            public ApiResponse<Map<Integer, Boolean>> batchReduceStock(List<ReduceStockRequest> request) {
                return error("batchReduceStock");
            }

            @Override
            public ApiResponse<Map<Integer, Boolean>> batchRestoreStock(List<ReduceStockRequest> request) {
                return error("batchRestoreStock");
            }

            private <T> ApiResponse<T> error(String op) {
                ApiResponse<T> response = new ApiResponse<>();
                response.setErrno(SERVICE_UNAVAILABLE_ERRNO);
                response.setErrmsg("goods-service unavailable on '" + op + "': " + cause.getMessage());
                return response;
            }
        };
    }
}
