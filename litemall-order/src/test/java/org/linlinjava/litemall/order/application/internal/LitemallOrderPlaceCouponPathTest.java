package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
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
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.CouponRedemption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.UsableCoupon;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The coupon seam in {@code placeOrder}, re-pointed from the dead local coupon
 * tables to the promotion facade (spec-coupon-checkout-contract.md):
 *
 * <ul>
 *   <li>a selected coupon promotion does not list as usable aborts BEFORE any
 *       order row is written (clean 422, coupon untouched);</li>
 *   <li>a usable coupon prices the order (coupon_price + reduced totals) with
 *       promotion's discount, and redemption happens only after the order row
 *       exists;</li>
 *   <li>a redeem-time rejection or discount drift aborts the placement;</li>
 *   <li>an order without a coupon never talks to promotion at all.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallOrderPlaceCouponPathTest {

    private static final int USER = 99;
    private static final int GOODS = 5;
    private static final int USER_COUPON = 3;
    private static final BigDecimal SUBTOTAL = new BigDecimal("100");

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

    private static LitemallPlaceOrderCommand command(Integer couponId, Integer userCouponId) {
        return new LitemallPlaceOrderCommand(USER, 0, 20, couponId, userCouponId, "", 0, 0, null);
    }

    @BeforeEach
    void wireCollaborators() {
        ReflectionTestUtils.setField(service, "cartServiceLayer", cartServiceLayer);
        // Wave 4: freight is priced by FreightCalculationService. A real instance with the
        // feature flag at its false default reproduces the legacy flat rule these tests
        // assert (SystemConfig min/value), without touching the mocked mappers.
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
        // Wave 7: placeOrder prices tax through the fail-closed TaxCalculationPort.
        // Zero-tax stub keeps these coupon assertions about coupons, not tax.
        org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort taxPort =
                org.mockito.Mockito.mock(
                        org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort.class);
        org.mockito.Mockito.when(taxPort.quote(org.mockito.ArgumentMatchers.any())).thenReturn(
                org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote.zero());
        ReflectionTestUtils.setField(service, "taxCalculationPort", taxPort);

        // Freight config: subtotal 100 ≥ min 88 → free shipping, keeps totals simple.
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
        when(cartLine.getProductId()).thenReturn(new LitemallGoodsProductId(7));
        when(cartLine.getNumber()).thenReturn(1);
        when(cartLine.getPrice()).thenReturn(new LitemallMoney(new BigDecimal("50")));
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

    /** An order row the impl reads back right after addOrder. */
    private LitemallOrderAggregate persistedOrder() {
        LitemallOrderAggregate existing = new LitemallOrderAggregate();
        existing.setOrderId(new LitemallOrderId(77));
        existing.setOrderSn("SN-1");
        return existing;
    }

    @Test
    void couponNotUsable_abortsBeforeAnyOrderRow() {
        when(promotionFacade.findUsableCoupon(any(), eq(USER_COUPON), any(), anySet(), anySet()))
                .thenReturn(Optional.empty());

        LitemallInvalidCouponException e = assertThrows(LitemallInvalidCouponException.class,
                () -> service.placeOrder(command(9, USER_COUPON)));

        assertTrue(e.getMessage().toLowerCase().contains("coupon"));
        verify(orderRepository, never()).addOrder(any());
        verify(promotionFacade, never()).redeemCoupon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void usableCoupon_pricesOrderWithPromotionDiscount_beforeRedeeming() {
        when(promotionFacade.findUsableCoupon(any(), eq(USER_COUPON), eq(SUBTOTAL),
                eq(Set.of(GOODS)), eq(Set.of(10))))
                .thenReturn(Optional.of(new UsableCoupon(USER_COUPON, 9, "Wave2",
                        new BigDecimal("15"), new BigDecimal("50"))));
        // Stop the flow right after the order insert: the read-back misses, proving
        // redeem could not have happened before the row existed.
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.placeOrder(command(9, USER_COUPON)));

        ArgumentCaptor<LitemallOrderAggregate> captor =
                ArgumentCaptor.forClass(LitemallOrderAggregate.class);
        verify(orderRepository).addOrder(captor.capture());
        LitemallOrderAggregate placed = captor.getValue();
        assertEquals(0, placed.getCouponPrice().getAmount().compareTo(new BigDecimal("15")));
        assertEquals(0, placed.getOrderPrice().getAmount().compareTo(new BigDecimal("85")));
        assertEquals(0, placed.getActualPrice().getAmount().compareTo(new BigDecimal("85")));
        verify(promotionFacade, never()).redeemCoupon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void redeemRejection_abortsPlacement_afterOrderRowExists() {
        when(promotionFacade.findUsableCoupon(any(), eq(USER_COUPON), any(), anySet(), anySet()))
                .thenReturn(Optional.of(new UsableCoupon(USER_COUPON, 9, "Wave2",
                        new BigDecimal("15"), new BigDecimal("50"))));
        when(orderRepository.findById(any())).thenReturn(Optional.of(persistedOrder()));
        when(promotionFacade.redeemCoupon(any(), eq(USER_COUPON), any(), eq(SUBTOTAL),
                anySet(), anySet()))
                .thenThrow(new LitemallInvalidCouponException(
                        "Failed to redeem coupon: Coupon is not usable"));

        LitemallInvalidCouponException e = assertThrows(LitemallInvalidCouponException.class,
                () -> service.placeOrder(command(9, USER_COUPON)));

        assertTrue(e.getMessage().toLowerCase().contains("coupon"));
        // Wave 18: redeem forwards the cart's scope facts (goods + category ids) so
        // promotion re-checks goods scope at consumption, not just at validation.
        verify(promotionFacade).redeemCoupon(any(), eq(USER_COUPON),
                eq(new LitemallOrderId(77)), eq(SUBTOTAL),
                eq(Set.of(GOODS)), eq(Set.of(10)));
        // Redeem was refused → nothing was consumed → nothing to release.
        verify(promotionFacade, never()).releaseCoupon(any(), any(), any());
    }

    @Test
    void redeemDiscountDrift_abortsPlacement() {
        when(promotionFacade.findUsableCoupon(any(), eq(USER_COUPON), any(), anySet(), anySet()))
                .thenReturn(Optional.of(new UsableCoupon(USER_COUPON, 9, "Wave2",
                        new BigDecimal("15"), new BigDecimal("50"))));
        when(orderRepository.findById(any())).thenReturn(Optional.of(persistedOrder()));
        when(promotionFacade.redeemCoupon(any(), eq(USER_COUPON), any(), eq(SUBTOTAL),
                anySet(), anySet()))
                .thenReturn(new CouponRedemption(USER_COUPON, 9, 77, new BigDecimal("10")));

        LitemallInvalidCouponException e = assertThrows(LitemallInvalidCouponException.class,
                () -> service.placeOrder(command(9, USER_COUPON)));
        assertTrue(e.getMessage().contains("discount changed"));
    }

    @Test
    void promotionDown_withCouponSelected_failsCleanly() {
        when(promotionFacade.findUsableCoupon(any(), eq(USER_COUPON), any(), anySet(), anySet()))
                .thenThrow(new LitemallPromotionServiceUnavailableException("usable-coupon lookup", null));

        assertThrows(LitemallPromotionServiceUnavailableException.class,
                () -> service.placeOrder(command(9, USER_COUPON)));
        verify(orderRepository, never()).addOrder(any());
    }

    @Test
    void noCouponSelected_neverTalksToPromotion() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        // Sentinels for "none": couponId 0 or -1, userCouponId 0/-1/null.
        assertThrows(NoSuchElementException.class,
                () -> service.placeOrder(command(0, -1)));

        // verifyNoMoreInteractions: Mockito-2-compatible equivalent (nothing was verified
        // on this mock, so it asserts zero interactions).
        verifyNoMoreInteractions(promotionFacade);
    }
}
