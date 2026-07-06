package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeBooleanResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeCancelRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeConfirmRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeConfirmResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeCreateRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeListResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeProductsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for {@link CjDisputeFeignClient}: every operation returns a
 * {@code result=false} envelope carrying the cause, so a CJ outage surfaces as a clean
 * {@code LitemallCjDisputeException} in the facade instead of a hang or a raw 500.
 */
@Component
public class CjDisputeFeignClientFallbackFactory implements FallbackFactory<CjDisputeFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(CjDisputeFeignClientFallbackFactory.class);
    private static final int SERVICE_UNAVAILABLE_CODE = 503;

    @Override
    public CjDisputeFeignClient create(Throwable cause) {
        log.error("cj-dropship-dispute circuit fallback engaged: {}", cause.toString());
        String detail = "CJ dispute API unavailable: " + cause.getMessage();
        return new CjDisputeFeignClient() {
            @Override
            public CjDisputeProductsResponse disputeProducts(String accessToken, String orderId) {
                CjDisputeProductsResponse response = new CjDisputeProductsResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage(detail);
                return response;
            }

            @Override
            public CjDisputeConfirmResponse disputeConfirmInfo(String accessToken, CjDisputeConfirmRequest request) {
                CjDisputeConfirmResponse response = new CjDisputeConfirmResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage(detail);
                return response;
            }

            @Override
            public CjDisputeBooleanResponse create(String accessToken, CjDisputeCreateRequest request) {
                return booleanFailure();
            }

            @Override
            public CjDisputeBooleanResponse cancel(String accessToken, CjDisputeCancelRequest request) {
                return booleanFailure();
            }

            @Override
            public CjDisputeListResponse getDisputeList(String accessToken, String orderId, int pageNum, int pageSize) {
                CjDisputeListResponse response = new CjDisputeListResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage(detail);
                return response;
            }

            private CjDisputeBooleanResponse booleanFailure() {
                CjDisputeBooleanResponse response = new CjDisputeBooleanResponse();
                response.setCode(SERVICE_UNAVAILABLE_CODE);
                response.setResult(false);
                response.setMessage(detail);
                return response;
            }
        };
    }
}
