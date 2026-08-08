package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException;
import org.linlinjava.litemall.order.application.util.exception.groupbuy.LitemallInvalidGroupSlotException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallCategoryId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderDomainService;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallPromotionFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuyCampaign;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuySlot;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Wave-21 group-buy seam in {@code placeOrder}
 * (spec-groupon-priced-submit-contract.md + the Wave-21 contract):
 *
 * <ul>
 *   <li>a valid, owned, free Pending/Success slot prices the campaign's line at
 *       {@code combinationPrice}, persists {@code pink_id} on the order and asks
 *       promotion to attach the order to the slot after placement;</li>
 *   <li>EVERY stale-slot condition — foreign slot, Failed/expired status, slot
 *       already consumed, goods mismatch, per-user quantity cap — is the typed
 *       {@link LitemallInvalidGroupSlotException} REJECT before any order row
 *       exists; the placement never silently re-prices at retail;</li>
 *   <li>promotion unreachable with a pinkId carried fails typed-503, like
 *       coupons;</li>
 *   <li>a submit without a pinkId never talks to the group-buy endpoints.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallOrderPlaceGroupBuyPathTest {

    private static final int USER = 99;
    private static final int GOODS = 5;
    private static final int PRODUCT = 7;
    private static final int PINK = 400;
    private static final int COMBINATION = 12;
    private static final BigDecimal SUBTOTAL = new BigDecimal("100");
    private static final BigDecimal GROUP_PRICE = new BigDecimal("30.00");

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponRepository grouponRepository;
    @Mock
    private LitemallCartRepository cartRepository;
    @Mock
    private LitemallAddressRepository addressRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private LitemallCouponServiceLayer couponService;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;

    @Mock
    private LitemallCartServiceLayer cartServiceLayer;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock
    private LitemallOrderDomainService orderDomainService;
    @Mock
    private LitemallGoodsFacade goodsFacade;
    @Mock
    private LitemallPromotionFacade promotionFacade;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver orderSourceResolver;
    @Mock
    private org.linlinjava.litemall.order.application.internal.cj.CjOrderAvailabilityChecker cjOrderAvailabilityChecker;

    @InjectMocks
    private LitemallOrderServiceImpl service;

    private LitemallCartAggregate cartLine;

    private static LitemallPlaceOrderCommand command(Integer pinkId) {
        return new LitemallPlaceOrderCommand(USER, 0, 20, 0, -1, "", 0, 0,
                null, null, null, null, null, null, pinkId);
    }

    private static GroupBuySlot slot(Integer userId, Integer orderId, String status) {
        return new GroupBuySlot(PINK, COMBINATION, userId, orderId, status);
    }

    private static GroupBuyCampaign campaign(Integer goodsId, Integer limitPerUser) {
        return new GroupBuyCampaign(COMBINATION, goodsId, GROUP_PRICE, limitPerUser);
    }

    @BeforeEach
    void wireCollaborators() {
        ReflectionTestUtils.setField(service, "cartServiceLayer", cartServiceLayer);
        ReflectionTestUtils.setField(service, "freightCalculationService",
                new FreightCalculationService(
                        org.mockito.Mockito.mock(org.linlinjava.litemall.db.dao.FreightTemplateMapper.class),
                        org.mockito.Mockito.mock(org.linlinjava.litemall.db.dao.LitemallGoodsMapper.class),
                        new org.linlinjava.litemall.order.infrastructure.configuration.FreightTemplateProperties()));
        ReflectionTestUtils.setField(service, "grouponServiceLayer", grouponServiceLayer);
        ReflectionTestUtils.setField(service, "orderDomainService", orderDomainService);
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "promotionFacade", promotionFacade);
        ReflectionTestUtils.setField(service, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(service, "orderSourceResolver", orderSourceResolver);
        ReflectionTestUtils.setField(service, "cjOrderAvailabilityChecker", cjOrderAvailabilityChecker);
        org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort taxPort =
                org.mockito.Mockito.mock(
                        org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort.class);
        org.mockito.Mockito.when(taxPort.quote(org.mockito.ArgumentMatchers.any())).thenReturn(
                org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote.zero());
        ReflectionTestUtils.setField(service, "taxCalculationPort", taxPort);

        Map<String, String> configs = new HashMap<>();
        configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_MIN, "88");
        configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_VALUE, "8");
        SystemConfig.setConfigs(configs);

        LitemallAddressAggregate address = mock(LitemallAddressAggregate.class);
        when(address.getName()).thenReturn("Bob");
        when(address.getTel()).thenReturn("123");
        when(address.getProvince()).thenReturn("P");
        when(address.getCity()).thenReturn("C");
        when(address.getCounty()).thenReturn("K");
        when(address.getAddressDetail()).thenReturn("D");
        when(address.getAddressId()).thenReturn(new LitemallAddressId(20));
        when(addressRepository.findAddress(any(), any())).thenReturn(address);

        cartLine = mock(LitemallCartAggregate.class);
        when(cartLine.getGoodsId()).thenReturn(new LitemallGoodsId(GOODS));
        when(cartLine.getProductId()).thenReturn(new LitemallGoodsProductId(PRODUCT));
        when(cartLine.getNumber()).thenReturn(1);
        when(cartLine.getPrice()).thenReturn(new LitemallMoney(new BigDecimal("50")));
        when(cartLine.getGoodsName()).thenReturn("Gadget");
        when(cartServiceLayer.getCheckedCartItems(any(), any())).thenReturn(List.of(cartLine));

        when(orderSourceResolver.resolve(anyList())).thenReturn("local");
        when(grouponServiceLayer.getGrouponRulesAggregate(any())).thenReturn(null);
        when(orderDomainService.priceCalculation(anyList(), any(), any())).thenReturn(SUBTOTAL);
        when(orderRepository.generateOrderSn(any())).thenReturn("SN-1");

        LitemallGoodsAggregate goods = new LitemallGoodsAggregate();
        goods.setGoodsId(new LitemallGoodsId(GOODS));
        goods.setCategoryId(new LitemallCategoryId(10));
        when(goodsFacade.batchGetGoods(anySet()))
                .thenReturn(Map.of(new LitemallGoodsId(GOODS), goods));
    }

    /** An order row the impl reads back right after addOrder — priced so the result maps. */
    private LitemallOrderAggregate persistedOrder() {
        LitemallOrderAggregate existing = new LitemallOrderAggregate();
        existing.setOrderId(new LitemallOrderId(77));
        existing.setOrderSn("SN-1");
        existing.setActualPrice(new LitemallMoney(new BigDecimal("30.00")));
        return existing;
    }

    /** Stubs so placeOrder can run its full stock-reserve tail for the happy path. */
    private void stubStockReservation() {
        LitemallGoodsProductAggregate product = new LitemallGoodsProductAggregate();
        product.setGoodsProductId(new LitemallGoodsProductId(PRODUCT));
        product.setNumber(10);
        product.setPrice(new LitemallMoney(new BigDecimal("50")));
        when(goodsFacade.getProductsByGoods(any())).thenReturn(List.of(product));
        when(goodsFacade.reduceStock(anyMap())).thenReturn(Map.of(PRODUCT, true));
    }

    // ---- happy path ------------------------------------------------------------

    @Test
    void validSlot_pricesLineAtCombinationPrice_persistsPinkId_andAttachesOrder() throws Exception {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(USER, null, "Pending")));
        when(promotionFacade.findCombination(COMBINATION))
                .thenReturn(Optional.of(campaign(GOODS, 2)));
        when(orderRepository.findById(any())).thenReturn(Optional.of(persistedOrder()));
        when(grouponServiceLayer.createGrouponOrder(any(), any(), any(), any())).thenReturn(null);
        stubStockReservation();

        service.placeOrder(command(PINK));

        // The campaign's line was re-priced to the promotion-authoritative group price.
        verify(cartLine).setPrice(eq(new LitemallMoney(GROUP_PRICE)));
        // The order row carries the slot (V56 pink_id).
        ArgumentCaptor<LitemallOrderAggregate> captor =
                ArgumentCaptor.forClass(LitemallOrderAggregate.class);
        verify(orderRepository).addOrder(captor.capture());
        assertEquals(PINK, captor.getValue().getPinkId());
        // Slot ↔ order linkage requested after placement (no active TX here → inline).
        verify(promotionFacade).attachOrderToPink(any(), eq(PINK), eq(new LitemallOrderId(77)));
    }

    // ---- reject branches (typed, never silent re-price) ------------------------

    @Test
    void unknownSlot_rejectsTyped_beforeAnyOrderRow() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK))).thenReturn(Optional.empty());

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertTrue(e.getMessage().contains("no longer available"));
        verify(orderRepository, never()).addOrder(any());
        verify(cartLine, never()).setPrice(any());
    }

    @Test
    void foreignSlot_rejectsTyped() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(12345, null, "Pending")));

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertTrue(e.getMessage().contains("another customer"));
        verify(orderRepository, never()).addOrder(any());
    }

    @Test
    void failedOrExpiredSlot_rejectsTyped_withExpiredFlavor() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(USER, null, "Failed")));

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertEquals("This group has expired — start a new one or buy at the regular price.",
                e.getMessage());
        verify(orderRepository, never()).addOrder(any());
        verify(cartLine, never()).setPrice(any());
    }

    @Test
    void slotAlreadyConsumedByAnotherOrder_rejectsTyped() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(USER, 55, "Pending")));

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertTrue(e.getMessage().contains("already used"));
        verify(orderRepository, never()).addOrder(any());
    }

    @Test
    void campaignGoodsMismatch_rejectsTyped() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(USER, null, "Pending")));
        when(promotionFacade.findCombination(COMBINATION))
                .thenReturn(Optional.of(campaign(999, null)));

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertTrue(e.getMessage().contains("different product"));
        verify(orderRepository, never()).addOrder(any());
        verify(cartLine, never()).setPrice(any());
    }

    @Test
    void quantityOverLimitPerUser_rejectsTyped() {
        when(cartLine.getNumber()).thenReturn(3);
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(USER, null, "Pending")));
        when(promotionFacade.findCombination(COMBINATION))
                .thenReturn(Optional.of(campaign(GOODS, 1)));

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertTrue(e.getMessage().contains("limited to 1"));
        verify(orderRepository, never()).addOrder(any());
        verify(cartLine, never()).setPrice(any());
    }

    @Test
    void vanishedCampaign_rejectsTyped() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenReturn(Optional.of(slot(USER, null, "Success")));
        when(promotionFacade.findCombination(COMBINATION)).thenReturn(Optional.empty());

        LitemallInvalidGroupSlotException e = assertThrows(LitemallInvalidGroupSlotException.class,
                () -> service.placeOrder(command(PINK)));

        assertTrue(e.getMessage().contains("campaign has ended"));
        verify(orderRepository, never()).addOrder(any());
    }

    @Test
    void promotionDown_withPinkIdCarried_failsCleanly() {
        when(promotionFacade.findGroupSlot(any(), eq(PINK)))
                .thenThrow(new LitemallPromotionServiceUnavailableException("group slot lookup", null));

        assertThrows(LitemallPromotionServiceUnavailableException.class,
                () -> service.placeOrder(command(PINK)));
        verify(orderRepository, never()).addOrder(any());
    }

    @Test
    void noPinkId_neverTalksToGroupBuyEndpoints() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.placeOrder(command(null)));

        verify(promotionFacade, never()).findGroupSlot(any(), any());
        verify(promotionFacade, never()).findCombination(any());
        verify(promotionFacade, never()).attachOrderToPink(any(), any(), any());
    }
}
