package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuyCampaign;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuySlot;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.PromotionServiceFeignClient;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The Wave-21 group-buy half of the promotion ACL: pink/campaign reads map
 * promotion's wire shapes (the pink GET answers the LEADER dto with the queried
 * slot possibly inside {@code members[]}), 404s become empty (typed stale-slot
 * reject upstream), transport failures become the typed 503 — and the
 * attach/release mutations are pure fail-soft compensation that never throws
 * (Wave-21 contract: tolerate promotion's endpoint arriving later).
 */
@ExtendWith(MockitoExtension.class)
class LitemallPromotionFacadeGroupBuyTest {

    private static final LitemallUserId USER = new LitemallUserId(42);
    private static final LitemallOrderId ORDER = new LitemallOrderId(77);

    @Mock
    private PromotionServiceFeignClient client;

    @InjectMocks
    private LitemallPromotionFacadeImpl facade;

    private static Response httpResponse(int status, String body) {
        Response.Builder builder = Response.builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "/test", new HashMap<>(),
                        null, StandardCharsets.UTF_8, null))
                .headers(new HashMap<>());
        if (body != null) {
            builder.body(body, StandardCharsets.UTF_8);
        }
        return builder.build();
    }

    // ---- findGroupSlot ---------------------------------------------------------

    @Test
    void leaderSlot_isMappedFromTheTopLevelDto() {
        when(client.pinkDetail(eq(42), eq(400))).thenReturn(httpResponse(200,
                "{\"pinkId\":400,\"combinationId\":12,\"headId\":null,\"userId\":42,"
                        + "\"orderId\":null,\"requiredMembers\":3,\"memberCount\":2,"
                        + "\"status\":\"Pending\",\"members\":[{\"pinkId\":401,\"userId\":7,"
                        + "\"combinationId\":12,\"orderId\":null,\"status\":\"Pending\"}]}"));

        Optional<GroupBuySlot> slot = facade.findGroupSlot(USER, 400);

        assertTrue(slot.isPresent());
        assertEquals(42, slot.get().getUserId());
        assertEquals(12, slot.get().getCombinationId());
        assertNull(slot.get().getOrderId());
        assertTrue(slot.get().isPayable());
    }

    @Test
    void memberSlot_isFoundInsideTheMembersArray() {
        when(client.pinkDetail(eq(42), eq(401))).thenReturn(httpResponse(200,
                "{\"pinkId\":400,\"combinationId\":12,\"userId\":7,\"orderId\":55,"
                        + "\"status\":\"Pending\",\"members\":[{\"pinkId\":401,\"userId\":42,"
                        + "\"combinationId\":12,\"orderId\":null,\"status\":\"Pending\"}]}"));

        Optional<GroupBuySlot> slot = facade.findGroupSlot(USER, 401);

        assertTrue(slot.isPresent());
        // The MEMBER's own facts, not the leader's.
        assertEquals(42, slot.get().getUserId());
        assertNull(slot.get().getOrderId());
    }

    @Test
    void failedSlot_isNotPayable() {
        when(client.pinkDetail(eq(42), eq(400))).thenReturn(httpResponse(200,
                "{\"pinkId\":400,\"combinationId\":12,\"userId\":42,\"orderId\":null,"
                        + "\"status\":\"Failed\",\"members\":[]}"));

        Optional<GroupBuySlot> slot = facade.findGroupSlot(USER, 400);

        assertTrue(slot.isPresent());
        assertFalse(slot.get().isPayable());
    }

    @Test
    void unknownPink_404_isEmpty() {
        when(client.pinkDetail(eq(42), eq(999))).thenReturn(httpResponse(404, null));

        assertTrue(facade.findGroupSlot(USER, 999).isEmpty());
    }

    @Test
    void transportFailure_becomesTypedUnavailable() {
        when(client.pinkDetail(any(), any())).thenThrow(new RuntimeException("connection refused"));

        assertThrows(LitemallPromotionServiceUnavailableException.class,
                () -> facade.findGroupSlot(USER, 400));
    }

    // ---- findCombination -------------------------------------------------------

    @Test
    void combination_isMapped_withTolerantLimitPerUser() {
        when(client.combinationDetail(12)).thenReturn(httpResponse(200,
                "{\"combinationId\":12,\"goodsId\":5,\"title\":\"T\","
                        + "\"combinationPrice\":30.00,\"originalPrice\":50.00,"
                        + "\"requiredMembers\":3,\"status\":\"Active\"}"));

        Optional<GroupBuyCampaign> campaign = facade.findCombination(12);

        assertTrue(campaign.isPresent());
        assertEquals(5, campaign.get().getGoodsId());
        assertEquals(0, new BigDecimal("30.00").compareTo(campaign.get().getCombinationPrice()));
        // Promotion's public DTO does not expose the cap yet — read as "no cap".
        assertNull(campaign.get().getLimitPerUser());
    }

    @Test
    void combinationWithLimitPerUser_capIsMapped() {
        when(client.combinationDetail(12)).thenReturn(httpResponse(200,
                "{\"combinationId\":12,\"goodsId\":5,\"combinationPrice\":30.00,\"limitPerUser\":2}"));

        assertEquals(2, facade.findCombination(12).orElseThrow().getLimitPerUser());
    }

    @Test
    void unknownCombination_404_isEmpty() {
        when(client.combinationDetail(999)).thenReturn(httpResponse(404, null));

        assertTrue(facade.findCombination(999).isEmpty());
    }

    // ---- attach-order / release (fail-soft mutations) --------------------------

    @Test
    void attachOrder_200_isConfirmed() {
        when(client.attachOrderToPink(eq(42), eq(400), any())).thenReturn(httpResponse(200,
                "{\"success\":true}"));

        assertTrue(facade.attachOrderToPink(USER, 400, ORDER));
    }

    @Test
    void attachOrder_404_whilePromotionCatchesUp_isToleratedNotThrown() {
        when(client.attachOrderToPink(eq(42), eq(400), any())).thenReturn(httpResponse(404, null));

        assertFalse(assertDoesNotThrow(() -> facade.attachOrderToPink(USER, 400, ORDER)));
    }

    @Test
    void attachOrder_transportFailure_isSwallowedFailSoft() {
        when(client.attachOrderToPink(any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertFalse(assertDoesNotThrow(() -> facade.attachOrderToPink(USER, 400, ORDER)));
    }

    @Test
    void releasePink_200_isConfirmed() {
        when(client.releasePinkSlot(eq(42), eq(400), any())).thenReturn(httpResponse(200,
                "{\"success\":true}"));

        assertTrue(facade.releasePinkSlot(USER, 400, ORDER));
    }

    @Test
    void releasePink_transportFailure_isSwallowedFailSoft() {
        when(client.releasePinkSlot(any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertFalse(assertDoesNotThrow(() -> facade.releasePinkSlot(USER, 400, ORDER)));
    }
}
