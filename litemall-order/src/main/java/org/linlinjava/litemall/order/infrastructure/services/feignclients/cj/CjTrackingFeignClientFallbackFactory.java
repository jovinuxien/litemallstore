package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.tracking.CjTrackInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link CjTrackingFeignClient}: a CJ outage / open breaker yields
 * a {@code result=false} response, which {@code CjTrackingFacadeImpl} maps to "no tracking info"
 * so the tracking endpoints degrade to a clean shipped-without-events payload, never a 5xx.
 */
@Component
public class CjTrackingFeignClientFallbackFactory implements FallbackFactory<CjTrackingFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(CjTrackingFeignClientFallbackFactory.class);
    private static final int SERVICE_UNAVAILABLE_CODE = 503;

    @Override
    public CjTrackingFeignClient create(Throwable cause) {
        log.warn("cj-dropship-tracking circuit fallback engaged: {}", cause.toString());
        return (accessToken, trackNumber) -> {
            CjTrackInfoResponse response = new CjTrackInfoResponse();
            response.setCode(SERVICE_UNAVAILABLE_CODE);
            response.setResult(false);
            response.setMessage("CJ trackInfo unavailable: " + cause.getMessage());
            return response;
        };
    }
}
