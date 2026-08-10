package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.system.SystemConfig;
import org.linlinjava.litemall.order.application.internal.cj.CjFreightQuoteService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallPromotionFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort;
import org.linlinjava.litemall.order.interfaces.dtos.cart.CheckoutSummaryDto;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Wave 24.1: the checkout preview adds the SAME courier upgrade delta submit charges —
 * {@code CjFreightQuoteService.upgradeDelta} is the one shared implementation, so
 * {@code GET /srv/cart/checkout?cjLogisticName=} previews exactly the freight the order
 * row will persist. Non-CJ carts and pickless previews stay on today's numbers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutSummaryUpgradeDeltaTest {

    private static final LitemallUserId USER = new LitemallUserId(99);

    @Mock
    private LitemallCartServiceLayer cartServiceLayer;
    @Mock
    private LitemallAddressRepository addressRepository;
    @Mock
    private TaxCalculationPort taxCalculationPort;
    @Mock
    private LitemallPromotionFacade promotionFacade;
    @Mock
    private LitemallGoodsFacade goodsFacade;
    @Mock
    private org.linlinjava.litemall.order.application.internal.cj.OrderSourceResolver orderSourceResolver;
    @Mock
    private CjFreightQuoteService cjFreightQuoteService;

    private final CheckoutSummaryService service = new CheckoutSummaryService();

    @BeforeEach
    void wireCollaborators() {
        ReflectionTestUtils.setField(service, "cartServiceLayer", cartServiceLayer);
        ReflectionTestUtils.setField(service, "addressRepository", addressRepository);
        // Real freight calculator, templates at their false default → the legacy flat rule.
        ReflectionTestUtils.setField(service, "freightCalculationService",
                new FreightCalculationService(
                        mock(org.linlinjava.litemall.db.dao.FreightTemplateMapper.class),
                        mock(org.linlinjava.litemall.db.dao.LitemallGoodsMapper.class),
                        new org.linlinjava.litemall.order.infrastructure.configuration.FreightTemplateProperties()));
        ReflectionTestUtils.setField(service, "taxCalculationPort", taxCalculationPort);
        ReflectionTestUtils.setField(service, "promotionFacade", promotionFacade);
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "orderSourceResolver", orderSourceResolver);
        ReflectionTestUtils.setField(service, "cjFreightQuoteService", cjFreightQuoteService);

        when(taxCalculationPort.enabled()).thenReturn(false);

        // Flat rule: free at/above 88, else 8. Subtotal here is 50 × 1 = 50 → flat 8.
        Map<String, String> configs = new HashMap<>();
        configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_MIN, "88");
        configs.put(SystemConfig.LITEMALL_EXPRESS_FREIGHT_VALUE, "8");
        SystemConfig.setConfigs(configs);

        LitemallCartAggregate cartLine = mock(LitemallCartAggregate.class);
        when(cartLine.isChecked()).thenReturn(true);
        when(cartLine.getGoodsId()).thenReturn(new LitemallGoodsId(5));
        when(cartLine.getProductId()).thenReturn(new LitemallGoodsProductId(7));
        when(cartLine.getNumber()).thenReturn(1);
        when(cartLine.getPrice()).thenReturn(new LitemallMoney(new BigDecimal("50")));
        when(cartServiceLayer.listAllCartItems(any())).thenReturn(List.of(cartLine));

        when(orderSourceResolver.resolve(anyList())).thenReturn(LitemallOrderAggregate.SOURCE_CJ);
    }

    @Test
    void cjPreviewWithPick_addsTheSharedDeltaOnTopOfTheFlatRule() {
        when(cjFreightQuoteService.upgradeDelta(eq("DE"),
                eq(List.of(new CjFreightQuoteService.QuoteItem(7, 1))),
                eq("DHL Express")))
                .thenReturn(new BigDecimal("3.20"));

        CheckoutSummaryDto dto = service.summarize(USER, null, null, "DE", "DHL Express");

        assertEquals(0, dto.getFreightPrice().compareTo(new BigDecimal("11.20")));
        assertEquals(0, dto.getOrderTotalPrice().compareTo(new BigDecimal("61.20")));
        assertEquals(0, dto.getActualPrice().compareTo(new BigDecimal("61.20")));
    }

    @Test
    void pickLessPreview_keepsTodaysNumber() {
        // The 4-arg overload (old SPA calls) routes through the same seam with no pick;
        // the shared implementation answers 0.00 for an absent name.
        when(cjFreightQuoteService.upgradeDelta(any(), anyList(), any()))
                .thenReturn(BigDecimal.ZERO.setScale(2));

        CheckoutSummaryDto dto = service.summarize(USER, null, null, "DE");

        assertEquals(0, dto.getFreightPrice().compareTo(new BigDecimal("8")));
    }

    @Test
    void localCart_neverConsultsTheCjDelta() {
        when(orderSourceResolver.resolve(anyList())).thenReturn(LitemallOrderAggregate.SOURCE_LOCAL);

        CheckoutSummaryDto dto = service.summarize(USER, null, null, "DE", "DHL Express");

        assertEquals(0, dto.getFreightPrice().compareTo(new BigDecimal("8")));
        verifyNoMoreInteractions(cjFreightQuoteService);
    }
}
