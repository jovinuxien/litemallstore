package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeBooleanResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeCancelRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeConfirmRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeConfirmResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeCreateRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeListResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute.CjDisputeProductsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CJ Dropshipping dispute client (see {@code developers.cjdropshipping.com} dispute API).
 * Same conventions as {@link CjOrderFeignClient}: config-driven host, per-call
 * {@code CJ-Access-Token}, circuit-breaker fallback for clean failure on CJ outage.
 */
@FeignClient(name = "cj-dropship-dispute", url = "${spring.cjdropship.api.base-url}",
        configuration = FeignConfig.class, fallbackFactory = CjDisputeFeignClientFallbackFactory.class)
public interface CjDisputeFeignClient {

    /** Lines of a CJ order eligible for dispute. */
    @GetMapping("/disputes/disputeProducts")
    CjDisputeProductsResponse disputeProducts(@RequestHeader("CJ-Access-Token") String accessToken,
                                              @RequestParam("orderId") String orderId);

    /** Claimable amounts + allowed expectations + selectable reasons for the given lines. */
    @PostMapping("/disputes/disputeConfirmInfo")
    CjDisputeConfirmResponse disputeConfirmInfo(@RequestHeader("CJ-Access-Token") String accessToken,
                                                @RequestBody CjDisputeConfirmRequest request);

    /** Open a dispute; data is a bare boolean, CJ's dispute id arrives later via the list. */
    @PostMapping("/disputes/create")
    CjDisputeBooleanResponse create(@RequestHeader("CJ-Access-Token") String accessToken,
                                    @RequestBody CjDisputeCreateRequest request);

    @PostMapping("/disputes/cancel")
    CjDisputeBooleanResponse cancel(@RequestHeader("CJ-Access-Token") String accessToken,
                                    @RequestBody CjDisputeCancelRequest request);

    /** Disputes for a CJ order (paged; page 1 with a generous size covers our per-order use). */
    @GetMapping("/disputes/getDisputeList")
    CjDisputeListResponse getDisputeList(@RequestHeader("CJ-Access-Token") String accessToken,
                                         @RequestParam("orderId") String orderId,
                                         @RequestParam("pageNum") int pageNum,
                                         @RequestParam("pageSize") int pageSize);
}
