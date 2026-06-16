package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * CJ Dropshipping create-order client. Host is config-driven ({@code spring.cjdropship.api.base-url});
 * the access token is supplied per call via the {@code CJ-Access-Token} header (from
 * {@link org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService}). A circuit-breaker
 * fallback ({@link CjOrderFeignClientFallbackFactory}) makes a CJ outage fail cleanly.
 */
@FeignClient(name = "cj-dropship-order", url = "${spring.cjdropship.api.base-url}",
        configuration = FeignConfig.class, fallbackFactory = CjOrderFeignClientFallbackFactory.class)
public interface CjOrderFeignClient {

    @PostMapping("/shopping/order/createOrder")
    CjCreateOrderResponse createOrder(@RequestHeader("CJ-Access-Token") String accessToken,
                                      @RequestBody CjCreateOrderRequest request);
}
