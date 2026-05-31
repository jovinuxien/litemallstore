package org.linlinjava.litemall.order.infrastructure.services.acl.facades;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.FeignResponseHandler;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.BatchGoodsRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.BatchProductsRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sole adapter between order code and the goods-management Feign client. Unwraps
 * the remote {@code ApiResponse} envelope and converts any transport/error
 * outcome into {@link LitemallGoodsServiceUnavailableException} so callers see a
 * clean domain failure instead of the raw Feign/protobuf exception.
 */
@Component
public class LitemallGoodsFacadeImpl implements LitemallGoodsFacade {

    private static final Logger log = LoggerFactory.getLogger(LitemallGoodsFacadeImpl.class);

    private final GoodsServiceFeignClient goodsServiceFeignClient;

    public LitemallGoodsFacadeImpl(GoodsServiceFeignClient goodsServiceFeignClient) {
        this.goodsServiceFeignClient = goodsServiceFeignClient;
    }

    @Override
    public LitemallGoodsProductAggregate getGoodsProduct(LitemallGoodsProductId productId) {
        return call(
                () -> goodsServiceFeignClient.getGoodsProductAggregate(productId.getId()),
                "Get goods product " + productId.getId());
    }

    @Override
    public Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoods(Set<Integer> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return call(
                () -> goodsServiceFeignClient.batchGetGoodsAggregates(new BatchGoodsRequest(goodsIds)),
                "Batch get goods");
    }

    @Override
    public Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> batchGetProducts(Set<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return call(
                () -> goodsServiceFeignClient.batchGetGoodsProductsAggregate(new BatchProductsRequest(productIds)),
                "Batch get products");
    }

    @Override
    public Map<Integer, Boolean> reduceStock(Map<Integer, Integer> productQuantities) {
        if (productQuantities == null || productQuantities.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ReduceStockRequest> requests = productQuantities.entrySet().stream()
                .map(e -> new ReduceStockRequest(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return call(
                () -> goodsServiceFeignClient.batchReduceStock(requests),
                "Batch reduce stock");
    }

    /**
     * Invoke the Feign client, unwrap the {@code ApiResponse}, and translate any
     * failure (error envelope from the circuit-breaker fallback, transport error,
     * null body) into {@link LitemallGoodsServiceUnavailableException}.
     */
    private <T> T call(java.util.function.Supplier<org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse<T>> remote,
                       String operation) {
        try {
            return FeignResponseHandler.handleResponse(remote.get(), operation);
        } catch (ServiceException e) {
            log.error("Goods ACL '{}' failed: {}", operation, e.getMessage());
            throw new LitemallGoodsServiceUnavailableException(operation, e);
        } catch (RuntimeException e) {
            // Feign transport errors (connect/read timeout, 5xx) surface here.
            log.error("Goods ACL '{}' transport failure", operation, e);
            throw new LitemallGoodsServiceUnavailableException(operation, e);
        }
    }
}
