package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 24 (EUR storefront): CJ freight amounts are converted USD → store currency at the
 * quote seam — every option's {@code logisticPrice} × {@code litemall.order.fx-usd-eur},
 * 2dp HALF_UP — so the chooser data, the headline quote, and anything downstream inherit
 * the conversion from this ONE place. Identity default keeps pre-Wave-24 behavior; a
 * non-positive rate is fail-safe identity (never zero or negate freight).
 */
@ExtendWith(MockitoExtension.class)
class CjFreightQuoteServiceFxTest {

    @Mock
    private CjDropshipOrderFacade facade;
    @Mock
    private CjOrderLineResolver lineResolver;

    private static final List<CjFreightQuoteService.QuoteItem> ITEMS =
            List.of(new CjFreightQuoteService.QuoteItem(101, 2));

    private CjFreightQuoteService service(String fx) {
        return new CjFreightQuoteService(facade, lineResolver, fx == null ? null : new BigDecimal(fx));
    }

    private void cjOffers(CjLogisticsOption... options) {
        when(lineResolver.resolveVid(101)).thenReturn("vid-101");
        when(facade.quoteLogisticsOptions(anyString(), any())).thenReturn(List.of(options));
    }

    @Test
    void fxHalf_halvesEveryOptionAmount_2dpHalfUp() {
        cjOffers(new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"),
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"));

        List<CjLogisticsOption> options = service("0.5").options("DE", ITEMS);

        assertEquals(2, options.size());
        // 5.99 × 0.5 = 2.995 → HALF_UP → 3.00 (not truncated to 2.99)
        assertEquals(new BigDecimal("3.00"), options.get(0).getLogisticPrice());
        assertEquals(new BigDecimal("11.70"), options.get(1).getLogisticPrice());
        // Non-money fields pass through untouched.
        assertEquals("CJPacket Ordinary", options.get(0).getLogisticName());
        assertEquals("8-12", options.get(0).getLogisticAging());
    }

    @Test
    void defaultIdentity_keepsRawAmounts() {
        cjOffers(new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"));

        List<CjLogisticsOption> options = service("1.0").options("DE", ITEMS);

        assertEquals(0, new BigDecimal("5.99").compareTo(options.get(0).getLogisticPrice()));
    }

    @Test
    void nullAmount_staysNull() {
        cjOffers(new CjLogisticsOption("CJPacket Ordinary", null, "8-12"));

        assertNull(service("0.5").options("DE", ITEMS).get(0).getLogisticPrice());
    }

    @Test
    void nonPositiveOrMissingRate_isFailSafeIdentity() {
        cjOffers(new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"));
        assertEquals(0, new BigDecimal("5.99")
                .compareTo(service("0").options("DE", ITEMS).get(0).getLogisticPrice()));

        cjOffers(new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"));
        assertEquals(0, new BigDecimal("5.99")
                .compareTo(service("-0.5").options("DE", ITEMS).get(0).getLogisticPrice()));

        cjOffers(new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"));
        assertEquals(0, new BigDecimal("5.99")
                .compareTo(service(null).options("DE", ITEMS).get(0).getLogisticPrice()));
    }

    @Test
    void convertedValuesAreCached_singleFacadeCall() {
        cjOffers(new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"));
        CjFreightQuoteService svc = service("0.5");

        List<CjLogisticsOption> first = svc.options("DE", ITEMS);
        List<CjLogisticsOption> second = svc.options("DE", ITEMS);

        assertEquals(new BigDecimal("3.00"), first.get(0).getLogisticPrice());
        // The cache stores the CONVERTED list — the second read must not re-fetch or re-convert.
        assertEquals(new BigDecimal("3.00"), second.get(0).getLogisticPrice());
        verify(facade, times(1)).quoteLogisticsOptions(anyString(), any());
    }
}
