package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.tracking.CjTrackInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CJ Dropshipping shipment-tracking client ({@code logistic/trackInfo}). Its own Feign client
 * (own circuit breaker + timeouts) so tracking-page traffic can never open the breaker the
 * order-placement path depends on. Read-heavy and slow-moving — answers are cached ~1h in
 * {@code CjTrackingFacadeImpl}.
 */
@FeignClient(name = "cj-dropship-tracking", url = "${spring.cjdropship.api.base-url}",
        configuration = FeignConfig.class, fallbackFactory = CjTrackingFeignClientFallbackFactory.class)
public interface CjTrackingFeignClient {

    @GetMapping("/logistic/trackInfo")
    CjTrackInfoResponse trackInfo(@RequestHeader("CJ-Access-Token") String accessToken,
                                  @RequestParam("trackNumber") String trackNumber);
}
