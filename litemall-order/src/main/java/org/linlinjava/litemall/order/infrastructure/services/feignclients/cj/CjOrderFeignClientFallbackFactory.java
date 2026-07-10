package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjBalanceResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderDetailResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderIdRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjSimpleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link CjOrderFeignClient}. When CJ is unreachable / times out /
 * the breaker is open, every method returns a {@code result=false} response carrying the cause;
 * {@code CjDropshipOrderFacadeImpl} then either raises a clean {@code LitemallCjOrderException}
 * (placement) or reports the miss to the caller (lifecycle ops, which the status-sync poller
 * simply retries next sweep) rather than hanging.
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
            public CjCreateOrderResponse createOrderV2(String accessToken, CjCreateOrderRequest request) {
                CjCreateOrderResponse response = new CjCreateOrderResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage("CJ create-order unavailable: " + cause.getMessage());
                return response;
            }

            @Override
            public CjSimpleResponse confirmOrder(String accessToken, CjOrderIdRequest request) {
                return simpleFailure("CJ confirmOrder unavailable: " + cause.getMessage());
            }

            @Override
            public CjOrderDetailResponse getOrderDetail(String accessToken, String orderId) {
                CjOrderDetailResponse response = new CjOrderDetailResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage("CJ getOrderDetail unavailable: " + cause.getMessage());
                return response;
            }

            @Override
            public CjSimpleResponse deleteOrder(String accessToken, String orderId) {
                return simpleFailure("CJ deleteOrder unavailable: " + cause.getMessage());
            }

            @Override
            public CjSimpleResponse payBalance(String accessToken, CjOrderIdRequest request) {
                return simpleFailure("CJ payBalance unavailable: " + cause.getMessage());
            }

            @Override
            public CjBalanceResponse getBalance(String accessToken) {
                CjBalanceResponse response = new CjBalanceResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage("CJ getBalance unavailable: " + cause.getMessage());
                return response;
            }

            @Override
            public CjFreightCalculateResponse freightCalculate(String accessToken,
                                                               CjFreightCalculateRequest request) {
                CjFreightCalculateResponse response = new CjFreightCalculateResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage("CJ freightCalculate unavailable: " + cause.getMessage());
                return response;
            }
        };
    }

    private static CjSimpleResponse simpleFailure(String message) {
        CjSimpleResponse response = new CjSimpleResponse();
        response.setCode(SERVICE_UNAVAILABLE_CODE);
        response.setResult(false);
        response.setMessage(message);
        return response;
    }
}
