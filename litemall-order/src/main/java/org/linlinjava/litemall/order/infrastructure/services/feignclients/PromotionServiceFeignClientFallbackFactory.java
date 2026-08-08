package org.linlinjava.litemall.order.infrastructure.services.feignclients;

import com.fasterxml.jackson.databind.JsonNode;
import feign.Response;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.CouponRedeemRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.CouponReleaseRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.PinkOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Circuit-breaker fallback for {@link PromotionServiceFeignClient}. A checkout that
 * carries a coupon must never be priced against guessed coupon data, and a redeem
 * whose outcome is unknown must not be assumed — so every method fails fast with the
 * typed {@link LitemallPromotionServiceUnavailableException}. (The redeem/release
 * methods only reach this fallback on transport-level failures: business 400s come
 * back as a normal {@link Response} and never trip the circuit.)
 */
@Component
public class PromotionServiceFeignClientFallbackFactory implements FallbackFactory<PromotionServiceFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(PromotionServiceFeignClientFallbackFactory.class);

    @Override
    public PromotionServiceFeignClient create(Throwable cause) {
        log.error("promotion-service circuit fallback engaged: {}", cause.toString());
        return new PromotionServiceFeignClient() {
            @Override
            public JsonNode usableCoupons(Integer userId, BigDecimal amount, String goodsIds, String categoryIds) {
                throw new LitemallPromotionServiceUnavailableException(
                        "usable-coupon lookup (circuit open/fallback)", cause);
            }

            @Override
            public JsonNode myCoupons(Integer userId, String status) {
                throw new LitemallPromotionServiceUnavailableException(
                        "my-coupons lookup (circuit open/fallback)", cause);
            }

            @Override
            public Response redeemCoupon(Integer userId, Integer userCouponId, CouponRedeemRequest body) {
                throw new LitemallPromotionServiceUnavailableException(
                        "redeem of user coupon " + userCouponId + " (circuit open/fallback)", cause);
            }

            @Override
            public Response releaseCoupon(Integer userId, Integer userCouponId, CouponReleaseRequest body) {
                throw new LitemallPromotionServiceUnavailableException(
                        "release of user coupon " + userCouponId + " (circuit open/fallback)", cause);
            }

            // Group-buy (Wave 21): a submit carrying a pinkId must never be priced at
            // retail because promotion is down — same fail-fast rule as coupons. The
            // attach/release mutations are fail-soft at the FACADE (logged), so a
            // typed throw here is still safe: the facade catches it.
            @Override
            public Response pinkDetail(Integer userId, Integer pinkId) {
                throw new LitemallPromotionServiceUnavailableException(
                        "group slot lookup for pink " + pinkId + " (circuit open/fallback)", cause);
            }

            @Override
            public Response combinationDetail(Integer combinationId) {
                throw new LitemallPromotionServiceUnavailableException(
                        "combination lookup " + combinationId + " (circuit open/fallback)", cause);
            }

            @Override
            public Response attachOrderToPink(Integer userId, Integer pinkId, PinkOrderRequest body) {
                throw new LitemallPromotionServiceUnavailableException(
                        "attach-order to pink " + pinkId + " (circuit open/fallback)", cause);
            }

            @Override
            public Response releasePinkSlot(Integer userId, Integer pinkId, PinkOrderRequest body) {
                throw new LitemallPromotionServiceUnavailableException(
                        "release of pink " + pinkId + " (circuit open/fallback)", cause);
            }
        };
    }
}
