package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.application.internal.cj.CjFreightQuoteService;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderDomainService;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallPromotionFacade;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Wave 24.1: submit composes charged CJ freight as flatComponent + upgradeDelta — the
 * SAME {@code CjFreightQuoteService.upgradeDelta} the checkout preview adds (single-
 * authority rule), so the persisted {@code freight_price} is exactly what the preview
 * showed:
 *
 * <ul>
 *   <li>below the free-shipping minimum: flat rule + the pick's exact delta;</li>
 *   <li>at/above the minimum (FREE_MIN): the delta ALONE — a free-shipping cart with an
 *       upgraded courier pays only the upgrade;</li>
 *   <li>a local (non-CJ) order never consults the CJ delta at all.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallOrderPlaceUpgradeDeltaFreightTest {

    private static final int USER = 99;
    private static final int GOODS = 5;
    private static final int PRODUCT = 7;

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
    @Mock
    private CjFreightQuoteService cjFreightQuoteService;

    @InjectMocks
    private LitemallOrderServiceImpl service;

    /** A CJ submit carrying the customer's courier pick (pre-Wave-21 ctor + pick). */
    private static LitemallPlaceOrderCommand command(String cjLogisticName) {
        return new LitemallPlaceOrderCommand(USER, 0, 20, 0, 0, "", 0, 0,
                "DE", cjLogisticName, null, null, null, null);
    }

    @BeforeEach
    void wireCollaborators() {
        ReflectionTestUtils.setField(service, "cartServiceLayer", cartServiceLayer);
        // Real freight calculator, templates at their false default → the legacy flat
        // rule these tests assert (SystemConfig min/value), as on the coupon-path test.
        ReflectionTestUtils.setField(service, "freightCalculationService",
                new FreightCalculationService(
                        mock(org.linlinjava.litemall.db.dao.FreightTemplateMapper.class),
                        mock(org.linlinjava.litemall.db.dao.LitemallGoodsMapper.class),
                        new org.linlinjava.litemall.order.infrastructure.configuration.FreightTemplateProperties()));
        ReflectionTestUtils.setField(service, "cjFreightQuoteService", cjFreightQuoteService);
        ReflectionTestUtils.setField(service, "grouponServiceLayer", grouponServiceLayer);
        ReflectionTestUtils.setField(service, "orderDomainService", orderDomainService);
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "promotionFacade", promotionFacade);
        ReflectionTestUtils.setField(service, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(service, "orderSourceResolver", orderSourceResolver);
        ReflectionTestUtils.setField(service, "cjOrderAvailabilityChecker", cjOrderAvailabilityChecker);
        org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort taxPort =
                mock(org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort.class);
        when(taxPort.quote(any())).thenReturn(
                org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote.zero());
        ReflectionTestUtils.setField(service, "taxCalculationPort", taxPort);

        // Flat rule: free at/above 88, else 8.
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

        LitemallCartAggregate cartLine = mock(LitemallCartAggregate.class);
        when(cartLine.getGoodsId()).thenReturn(new LitemallGoodsId(GOODS));
        when(cartLine.getProductId()).thenReturn(new LitemallGoodsProductId(PRODUCT));
        when(cartLine.getNumber()).thenReturn(1);
        when(cartLine.getPrice()).thenReturn(new LitemallMoney(new BigDecimal("50")));
        when(cartServiceLayer.getCheckedCartItems(any(), any())).thenReturn(List.of(cartLine));

        when(orderSourceResolver.resolve(anyList())).thenReturn(LitemallOrderAggregate.SOURCE_CJ);
        when(grouponServiceLayer.getGrouponRulesAggregate(any())).thenReturn(null);
        when(orderRepository.generateOrderSn(any())).thenReturn("SN-1");
        // Stop the flow right after the order insert (the read-back misses): the captured
        // addOrder row already carries the priced freight these tests assert.
        when(orderRepository.findById(any())).thenReturn(Optional.empty());
    }

    private void subtotal(String amount) {
        when(orderDomainService.priceCalculation(anyList(), any(), any()))
                .thenReturn(new BigDecimal(amount));
    }

    private LitemallOrderAggregate placedOrder(LitemallPlaceOrderCommand command) {
        assertThrows(NoSuchElementException.class, () -> service.placeOrder(command));
        ArgumentCaptor<LitemallOrderAggregate> captor =
                ArgumentCaptor.forClass(LitemallOrderAggregate.class);
        verify(orderRepository).addOrder(captor.capture());
        return captor.getValue();
    }

    @Test
    void cjPick_chargesFlatPlusExactDelta_andPersistsIt() {
        subtotal("50"); // below the 88 minimum → flat 8
        when(cjFreightQuoteService.upgradeDelta(eq("DE"),
                eq(List.of(new CjFreightQuoteService.QuoteItem(PRODUCT, 1))),
                eq("DHL Express")))
                .thenReturn(new BigDecimal("3.20"));

        LitemallOrderAggregate placed = placedOrder(command("DHL Express"));

        assertEquals(0, placed.getFreightPrice().getAmount().compareTo(new BigDecimal("11.20")));
        // The full charged amount rides the total exactly as previewed: 50 + 11.20.
        assertEquals(0, placed.getOrderPrice().getAmount().compareTo(new BigDecimal("61.20")));
        assertEquals(0, placed.getActualPrice().getAmount().compareTo(new BigDecimal("61.20")));
    }

    @Test
    void freeShippingCart_withUpgradedCourier_chargesOnlyTheDelta() {
        subtotal("100"); // ≥ 88 → FREE_MIN flat component 0
        when(cjFreightQuoteService.upgradeDelta(any(), anyList(), any()))
                .thenReturn(new BigDecimal("3.20"));

        LitemallOrderAggregate placed = placedOrder(command("DHL Express"));

        assertEquals(0, placed.getFreightPrice().getAmount().compareTo(new BigDecimal("3.20")));
        assertEquals(0, placed.getOrderPrice().getAmount().compareTo(new BigDecimal("103.20")));
    }

    @Test
    void noPick_staysOnTodaysFlatCharge() {
        subtotal("50");
        // The shared implementation owns the absent-name rule (0.00) — submit still asks
        // it, keeping ONE authority for the delta decision.
        when(cjFreightQuoteService.upgradeDelta(any(), anyList(), any()))
                .thenReturn(BigDecimal.ZERO.setScale(2));

        LitemallOrderAggregate placed = placedOrder(command(null));

        assertEquals(0, placed.getFreightPrice().getAmount().compareTo(new BigDecimal("8")));
    }

    @Test
    void localOrder_neverConsultsTheCjDelta() {
        subtotal("50");
        when(orderSourceResolver.resolve(anyList())).thenReturn(LitemallOrderAggregate.SOURCE_LOCAL);

        LitemallOrderAggregate placed = placedOrder(command("DHL Express"));

        assertEquals(0, placed.getFreightPrice().getAmount().compareTo(new BigDecimal("8")));
        verifyNoMoreInteractions(cjFreightQuoteService);
    }
}
