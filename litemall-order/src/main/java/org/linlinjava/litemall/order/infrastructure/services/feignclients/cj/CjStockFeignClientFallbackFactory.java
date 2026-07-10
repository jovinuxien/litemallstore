package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.stock.CjStockQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link CjStockFeignClient}: a CJ outage / open breaker yields a
 * {@code result=false} response, which {@code CjStockFacadeImpl} maps to "stock unknown" so the
 * advisory submit-time check proceeds instead of blocking checkout.
 */
@Component
public class CjStockFeignClientFallbackFactory implements FallbackFactory<CjStockFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(CjStockFeignClientFallbackFactory.class);
    private static final int SERVICE_UNAVAILABLE_CODE = 503;

    @Override
    public CjStockFeignClient create(Throwable cause) {
        log.warn("cj-dropship-stock circuit fallback engaged: {}", cause.toString());
        return (accessToken, vid) -> {
            CjStockQueryResponse response = new CjStockQueryResponse();
            response.setCode(SERVICE_UNAVAILABLE_CODE);
            response.setResult(false);
            response.setMessage("CJ stock query unavailable: " + cause.getMessage());
            return response;
        };
    }
}
