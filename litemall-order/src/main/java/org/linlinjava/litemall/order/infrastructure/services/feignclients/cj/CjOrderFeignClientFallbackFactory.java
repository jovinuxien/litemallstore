package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link CjOrderFeignClient}. When CJ is unreachable / times out /
 * the breaker is open, {@code createOrder} returns a {@code result=false} response carrying the
 * cause; {@code CjDropshipOrderFacadeImpl} then raises a {@code LitemallCjOrderException} so a CJ
 * placement fails cleanly rather than hanging.
 */
@Component
public class CjOrderFeignClientFallbackFactory implements FallbackFactory<CjOrderFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(CjOrderFeignClientFallbackFactory.class);
    private static final int SERVICE_UNAVAILABLE_CODE = 503;

    @Override
    public CjOrderFeignClient create(Throwable cause) {
        log.error("cj-dropship-order circuit fallback engaged: {}", cause.toString());
        return new CjOrderFeignClient() {
            @Override
            public CjCreateOrderResponse createOrder(String accessToken, CjCreateOrderRequest request) {
                CjCreateOrderResponse response = new CjCreateOrderResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage("CJ create-order unavailable: " + cause.getMessage());
                return response;
            }

            @Override
            public org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateResponse freightCalculate(
                    String accessToken,
                    org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateRequest request) {
                var response = new org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage("CJ freightCalculate unavailable: " + cause.getMessage());
                return response;
            }
        };
    }
}
