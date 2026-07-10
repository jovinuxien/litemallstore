package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.CouponRedemption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.UsableCoupon;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.PromotionServiceFeignClient;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The promotion ACL's outcome translation, against the wire contract in
 * {@code litemall-promotion-service/docs/spec-coupon-checkout-contract.md}:
 * business 400s on redeem become the typed 422 coupon rejection (never
 * "unavailable"), transport failures become the typed 503, and release — pure
 * compensation — never throws at all.
 */
@ExtendWith(MockitoExtension.class)
class LitemallPromotionFacadeImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LitemallUserId USER = new LitemallUserId(42);
    private static final LitemallOrderId ORDER = new LitemallOrderId(77);

    @Mock
    private PromotionServiceFeignClient client;

    @InjectMocks
    private LitemallPromotionFacadeImpl facade;

    private static Response httpResponse(int status, String body) {
        return Response.builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.POST, "/test", new HashMap<>(),
                        null, StandardCharsets.UTF_8, null))
                .headers(new HashMap<>())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    // ---- findUsableCoupon ------------------------------------------------------

    @Test
    void usableCoupon_selectedHoldingPresent_isMappedWithDiscount() throws Exception {
        when(client.usableCoupons(eq(42), eq(new BigDecimal("100")), eq("5"), eq("10")))
                .thenReturn(MAPPER.readTree(
                        "[{\"couponId\":9,\"userCouponId\":3,\"name\":\"Wave2\",\"discount\":15,\"min\":50},"
                        + "{\"couponId\":8,\"userCouponId\":2,\"name\":\"Other\",\"discount\":5,\"min\":10}]"));

        Optional<UsableCoupon> found = facade.findUsableCoupon(
                USER, 3, new BigDecimal("100"), Set.of(5), Set.of(10));

        assertTrue(found.isPresent());
        assertEquals(9, found.get().getCouponId());
        assertEquals(3, found.get().getUserCouponId());
        assertEquals(0, found.get().getDiscount().compareTo(new BigDecimal("15")));
    }

    @Test
    void usableCoupon_selectedHoldingAbsentFromList_isEmpty() throws Exception {
        when(client.usableCoupons(anyInt(), any(), any(), any()))
                .thenReturn(MAPPER.readTree("[{\"couponId\":8,\"userCouponId\":2,\"discount\":5}]"));

        assertTrue(facade.findUsableCoupon(USER, 3, new BigDecimal("100"), Set.of(5), Set.of())
                .isEmpty());
    }

    @Test
    void usableCoupon_transportFailure_raisesUnavailable_neverEmpty() {
        // Empty would read as "coupon invalid" (422) — an outage must stay a 503.
        when(client.usableCoupons(anyInt(), any(), any(), any()))
                .thenThrow(new RuntimeException("connect timed out"));

        assertThrows(LitemallPromotionServiceUnavailableException.class,
                () -> facade.findUsableCoupon(USER, 3, new BigDecimal("100"), Set.of(5), Set.of()));
    }

    // ---- redeemCoupon ----------------------------------------------------------

    @Test
    void redeem_success_returnsAuthoritativeDiscountAndStampedOrder() {
        when(client.redeemCoupon(eq(42), eq(3), any())).thenReturn(httpResponse(200,
                "{\"success\":true,\"operationType\":\"REDEEM_COUPON\",\"message\":\"ok\","
                + "\"data\":{\"userCouponId\":3,\"couponId\":9,\"orderId\":77,\"discount\":15}}"));

        CouponRedemption redemption = facade.redeemCoupon(USER, 3, ORDER, new BigDecimal("100"));

        assertEquals(3, redemption.getUserCouponId());
        assertEquals(9, redemption.getCouponId());
        assertEquals(77, redemption.getOrderId());
        assertEquals(0, redemption.getDiscount().compareTo(new BigDecimal("15")));
    }

    @Test
    void redeem_business400_raisesInvalidCoupon_withPromotionMessage() {
        when(client.redeemCoupon(eq(42), eq(3), any())).thenReturn(httpResponse(400,
                "{\"success\":false,\"operationType\":\"REDEEM_COUPON\","
                + "\"message\":\"Failed to redeem coupon: Coupon is not usable\"}"));

        LitemallInvalidCouponException e = assertThrows(LitemallInvalidCouponException.class,
                () -> facade.redeemCoupon(USER, 3, ORDER, new BigDecimal("100")));
        assertTrue(e.getMessage().contains("Coupon is not usable"));
    }

    @Test
    void redeem_unexpectedStatus_raisesUnavailable() {
        when(client.redeemCoupon(eq(42), eq(3), any()))
                .thenReturn(httpResponse(500, "boom"));

        assertThrows(LitemallPromotionServiceUnavailableException.class,
                () -> facade.redeemCoupon(USER, 3, ORDER, new BigDecimal("100")));
    }

    // ---- releaseCoupon (compensation: never throws) ----------------------------

    @Test
    void release_success_confirms() {
        when(client.releaseCoupon(eq(42), eq(3), any())).thenReturn(httpResponse(200,
                "{\"success\":true,\"operationType\":\"RELEASE_COUPON\",\"message\":\"ok\","
                + "\"data\":{\"userCouponId\":3,\"couponId\":9}}"));

        assertTrue(facade.releaseCoupon(USER, 3, ORDER));
    }

    @Test
    void release_alreadyReleased_isIdempotentSuccess() {
        when(client.releaseCoupon(eq(42), eq(3), any())).thenReturn(httpResponse(200,
                "{\"success\":true,\"operationType\":\"RELEASE_COUPON\",\"message\":\"ok\","
                + "\"data\":{\"userCouponId\":3,\"couponId\":9,\"alreadyReleased\":true}}"));

        assertTrue(facade.releaseCoupon(USER, 3, ORDER));
    }

    @Test
    void release_refusedBecauseRespent_returnsFalseWithoutThrowing() {
        when(client.releaseCoupon(eq(42), eq(3), any())).thenReturn(httpResponse(400,
                "{\"success\":false,\"operationType\":\"RELEASE_COUPON\","
                + "\"message\":\"Failed to release coupon: Coupon was not redeemed by this order.\"}"));

        assertFalse(facade.releaseCoupon(USER, 3, ORDER));
    }

    @Test
    void release_transportFailure_returnsFalseWithoutThrowing() {
        when(client.releaseCoupon(eq(42), eq(3), any()))
                .thenThrow(new LitemallPromotionServiceUnavailableException("release", null));

        assertFalse(facade.releaseCoupon(USER, 3, ORDER));
    }

    // ---- findRedeemedUserCouponForOrder ----------------------------------------

    @Test
    void redeemedCouponLookup_matchesByConsumingOrder() throws Exception {
        when(client.myCoupons(eq(42), anyString())).thenReturn(MAPPER.readTree(
                "[{\"userCouponId\":2,\"couponId\":8,\"status\":\"USED\",\"orderId\":50},"
                + "{\"userCouponId\":3,\"couponId\":9,\"status\":\"USED\",\"orderId\":77}]"));

        assertEquals(Optional.of(3), facade.findRedeemedUserCouponForOrder(USER, ORDER));
    }

    @Test
    void redeemedCouponLookup_failureIsEmpty_bestEffort() {
        when(client.myCoupons(anyInt(), anyString())).thenThrow(new RuntimeException("down"));

        assertTrue(facade.findRedeemedUserCouponForOrder(USER, ORDER).isEmpty());
    }
}
