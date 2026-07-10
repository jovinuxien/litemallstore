package org.linlinjava.litemall.order.infrastructure.services.feignclients;

import com.fasterxml.jackson.databind.JsonNode;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link GoodsServiceFeignClient}. On the placement path any
 * degraded/synthetic goods data would be actively dangerous (an order must never be created
 * against unvalidated price or stock), so the read fallbacks fail fast with the typed
 * {@link LitemallGoodsServiceUnavailableException} the goods ACL already propagates, and the
 * {@code reduceStock} fallback returns a non-zero errno so the caller treats the product as
 * an unconfirmed reservation (same {@code result=false} pattern as the CJ fallbacks).
 */
@Component
public class GoodsServiceFeignClientFallbackFactory implements FallbackFactory<GoodsServiceFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(GoodsServiceFeignClientFallbackFactory.class);
    private static final int SERVICE_UNAVAILABLE_CODE = 503;

    @Override
    public GoodsServiceFeignClient create(Throwable cause) {
        log.error("goods-service circuit fallback engaged: {}", cause.toString());
        return new GoodsServiceFeignClient() {
            @Override
            public JsonNode getGoodsDetail(Integer goodsId) {
                throw new LitemallGoodsServiceUnavailableException(
                        "get goods detail " + goodsId + " (circuit open/fallback)", cause);
            }

            @Override
            public JsonNode getProductsByGoods(Integer goodsId) {
                throw new LitemallGoodsServiceUnavailableException(
                        "get products for goods " + goodsId + " (circuit open/fallback)", cause);
            }

            @Override
            public ApiResponse<Void> reduceStock(ReduceStockRequest request) {
                return ApiResponse.fail(SERVICE_UNAVAILABLE_CODE,
                        "goods-service stock reduce unavailable: " + cause.getMessage());
            }

            @Override
            public ApiResponse<Void> restoreStock(ReduceStockRequest request) {
                // Compensation is best-effort: report unrestored, never throw.
                return ApiResponse.fail(SERVICE_UNAVAILABLE_CODE,
                        "goods-service stock restore unavailable: " + cause.getMessage());
            }
        };
    }
}
