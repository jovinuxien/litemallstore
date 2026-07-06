package org.linlinjava.litemall.order.infrastructure.services.feignclients;

import com.fasterxml.jackson.databind.JsonNode;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Outbound client for goods-management's REAL contract (base {@code /srv/goods}).
 * goods-management returns raw, un-enveloped aggregates here, and its product/stock
 * surface is goodsId-centric with only a single (non-batch) reduce — the order side
 * absorbs that impedance in {@link org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade}.
 * Responses come back as {@link JsonNode} so the facade maps exactly the fields it
 * needs, side-stepping cross-service DTO/format mismatches (LitemallMoney, date
 * arrays, snake/camel field names). Every call carries a machine token added by
 * {@link FeignConfig#goodsMachineTokenInterceptor}.
 */
@FeignClient(name = "goods-service", url = "${goods.service.url}", configuration = FeignConfig.class,
        fallbackFactory = GoodsServiceFeignClientFallbackFactory.class)
public interface GoodsServiceFeignClient {

    /** GET /srv/goods/goodsdetail?id={goodsId} → the goods aggregate (raw JSON). */
    @GetMapping("/srv/goods/goodsdetail")
    JsonNode getGoodsDetail(@RequestParam("id") Integer goodsId);

    /** GET /srv/goods/product?id={goodsId} → JSON array of this goods' product variants. */
    @GetMapping("/srv/goods/product")
    JsonNode getProductsByGoods(@RequestParam("id") Integer goodsId);

    /** POST /srv/goods/stock/reduce → {@code {errno,data,errmsg}} (errno 0 = reduced). */
    @PostMapping("/srv/goods/stock/reduce")
    ApiResponse<Void> reduceStock(@RequestBody ReduceStockRequest request);
}
