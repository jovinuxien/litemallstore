package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjBalanceResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderDetailResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderIdRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjSimpleResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CJ Dropshipping shopping-order client. Host is config-driven ({@code spring.cjdropship.api.base-url});
 * the access token is supplied per call via the {@code CJ-Access-Token} header (from
 * {@link org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService}). A circuit-breaker
 * fallback ({@link CjOrderFeignClientFallbackFactory}) makes a CJ outage fail cleanly.
 *
 * <p>{@code confirmOrder} is HTTP PATCH, which the default JDK Feign transport cannot send —
 * Feign runs on OkHttp here ({@code spring.cloud.openfeign.okhttp.enabled} + {@code feign-okhttp}).
 */
@FeignClient(name = "cj-dropship-order", url = "${spring.cjdropship.api.base-url}",
        configuration = FeignConfig.class, fallbackFactory = CjOrderFeignClientFallbackFactory.class)
public interface CjOrderFeignClient {

    /**
     * Wave-3 order creation ({@code createOrderV2}); the request's {@code payType} decides
     * whether money moves (we always send 3 = create-only draft). Same envelope semantics
     * as the legacy {@code createOrder} it replaced.
     */
    @PostMapping("/shopping/order/createOrderV2")
    CjCreateOrderResponse createOrderV2(@RequestHeader("CJ-Access-Token") String accessToken,
                                        @RequestBody CjCreateOrderRequest request);

    /** Confirm a CREATED order (CJ moves it toward UNPAID; a precondition of payBalance). */
    @PatchMapping("/shopping/order/confirmOrder")
    CjSimpleResponse confirmOrder(@RequestHeader("CJ-Access-Token") String accessToken,
                                  @RequestBody CjOrderIdRequest request);

    /** Full CJ-side order detail — the status-sync source of truth (accepts CJ or merchant id). */
    @GetMapping("/shopping/order/getOrderDetail")
    CjOrderDetailResponse getOrderDetail(@RequestHeader("CJ-Access-Token") String accessToken,
                                         @RequestParam("orderId") String orderId);

    /** Delete a CJ order; CJ allows it only while the order is still CREATED / IN_CART. */
    @DeleteMapping("/shopping/order/deleteOrder")
    CjSimpleResponse deleteOrder(@RequestHeader("CJ-Access-Token") String accessToken,
                                 @RequestParam("orderId") String orderId);

    /** Pay an UNPAID order from the CJ account balance. */
    @PostMapping("/shopping/pay/payBalance")
    CjSimpleResponse payBalance(@RequestHeader("CJ-Access-Token") String accessToken,
                                @RequestBody CjOrderIdRequest request);

    /** CJ account balance (admin readout). */
    @GetMapping("/shopping/pay/getBalance")
    CjBalanceResponse getBalance(@RequestHeader("CJ-Access-Token") String accessToken);

    /**
     * Logistics lines CJ actually offers for a product/destination combination. createOrder
     * rejects any {@code logisticName} not in this list (code 1605001), so placement resolves
     * the line here first.
     */
    @PostMapping("/logistic/freightCalculate")
    CjFreightCalculateResponse freightCalculate(@RequestHeader("CJ-Access-Token") String accessToken,
                                                @RequestBody CjFreightCalculateRequest request);
}
