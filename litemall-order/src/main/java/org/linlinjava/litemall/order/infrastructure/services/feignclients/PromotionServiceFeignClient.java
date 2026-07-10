package org.linlinjava.litemall.order.infrastructure.services.feignclients;

import com.fasterxml.jackson.databind.JsonNode;
import feign.Response;
import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.CouponRedeemRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.CouponReleaseRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Outbound client for the promotion service's coupon checkout contract
 * ({@code litemall-promotion-service/docs/spec-coupon-checkout-contract.md}).
 * Every call carries the machine token (added by
 * {@link FeignConfig#goodsMachineTokenInterceptor} — the client name doesn't start
 * with {@code cj-}) plus the customer's forwarded identity in {@code X-User-Id};
 * promotion enforces ownership from that header, never from the body.
 *
 * <p>The redeem/release mutations answer HTTP 400 with a business envelope
 * ({@code {success:false, message, ...}}) that the order side must read — a 400 is
 * a coupon rejection, not an outage. They therefore return raw {@link Response}
 * (which bypasses the error decoder and the fallback for non-2xx statuses) so
 * {@code LitemallPromotionFacadeImpl} can tell the two apart. The reads return
 * {@link JsonNode} like the goods client; any decoded failure lands in the
 * fallback factory as service-unavailable.
 */
@FeignClient(name = "promotion-service", url = "${promotion.service.url}",
        configuration = FeignConfig.class,
        fallbackFactory = PromotionServiceFeignClientFallbackFactory.class)
public interface PromotionServiceFeignClient {

    /** GET /srv/promotion/coupon/usable → JSON array of coupons usable for this checkout. */
    @GetMapping("/srv/promotion/coupon/usable")
    JsonNode usableCoupons(@RequestHeader("X-User-Id") Integer userId,
                           @RequestParam("amount") BigDecimal amount,
                           @RequestParam(value = "goodsIds", required = false) String goodsIds,
                           @RequestParam(value = "categoryIds", required = false) String categoryIds);

    /** GET /srv/promotion/coupon/my → the user's held coupons (each carries status + orderId). */
    @GetMapping("/srv/promotion/coupon/my")
    JsonNode myCoupons(@RequestHeader("X-User-Id") Integer userId,
                       @RequestParam(value = "status", required = false) String status);

    /** POST redeem: exactly-once USABLE→USED. 200 = redeemed, 400 = business rejection. */
    @PostMapping("/srv/promotion/coupon/user/{userCouponId}/redeem")
    Response redeemCoupon(@RequestHeader("X-User-Id") Integer userId,
                          @PathVariable("userCouponId") Integer userCouponId,
                          @RequestBody CouponRedeemRequest body);

    /** POST release: idempotent, replay-safe compensation. 200 = released (or already), 400 = refused. */
    @PostMapping("/srv/promotion/coupon/user/{userCouponId}/release")
    Response releaseCoupon(@RequestHeader("X-User-Id") Integer userId,
                           @PathVariable("userCouponId") Integer userCouponId,
                           @RequestBody CouponReleaseRequest body);
}
