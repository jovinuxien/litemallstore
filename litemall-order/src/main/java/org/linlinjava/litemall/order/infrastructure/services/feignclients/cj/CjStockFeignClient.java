package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.stock.CjStockQueryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CJ Dropshipping variant-stock client, used by the ADVISORY submit-time stock check.
 * Deliberately a separate Feign client (own circuit breaker + timeouts) from
 * {@code cj-dropship-order}: a burst of failing stock lookups must never open the breaker
 * that {@code createOrderV2} placement depends on.
 */
@FeignClient(name = "cj-dropship-stock", url = "${spring.cjdropship.api.base-url}",
        configuration = FeignConfig.class, fallbackFactory = CjStockFeignClientFallbackFactory.class)
public interface CjStockFeignClient {

    @GetMapping("/product/stock/queryByVid")
    CjStockQueryResponse queryByVid(@RequestHeader("CJ-Access-Token") String accessToken,
                                    @RequestParam("vid") String vid);
}
