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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Wave 24.1 (upgrade-delta freight): {@code upgradeDelta} is the ONE implementation both
 * the checkout preview and submit add on top of the flat-rule freight — max(0, selected −
 * default) over the post-fx option list. Everything unpriceable (absent/unknown name, CJ
 * outage, null prices) must yield 0.00 — today's flat charge, never an error.
 */
@ExtendWith(MockitoExtension.class)
class CjFreightQuoteServiceUpgradeDeltaTest {

    @Mock
    private CjDropshipOrderFacade facade;
    @Mock
    private CjOrderLineResolver lineResolver;

    private static final List<CjFreightQuoteService.QuoteItem> ITEMS =
            List.of(new CjFreightQuoteService.QuoteItem(101, 2));

    private CjFreightQuoteService service(String fx) {
        return new CjFreightQuoteService(facade, lineResolver, fx == null ? null : new BigDecimal(fx));
    }

    /** CJ offers these lines, and the default pick resolves by name from the CONVERTED list. */
    private void cjOffers(String defaultName, CjLogisticsOption... options) {
        when(lineResolver.resolveVid(101)).thenReturn("vid-101");
        when(facade.quoteLogisticsOptions(anyString(), any())).thenReturn(List.of(options));
        when(facade.chooseLogistics(any(), isNull())).thenAnswer(inv -> {
            List<CjLogisticsOption> offered = inv.getArgument(0);
            return offered == null ? null : offered.stream()
                    .filter(o -> defaultName.equalsIgnoreCase(o.getLogisticName()))
                    .findFirst().orElse(null);
        });
    }

    @Test
    void pricierPick_chargesTheExactDifference_2dp() {
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"),
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"));

        assertEquals(new BigDecimal("17.41"),
                service("1.0").upgradeDelta("DE", ITEMS, "DHL Express"));
    }

    @Test
    void pickMatchesCaseInsensitively_andTrimmed() {
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"),
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"));

        assertEquals(new BigDecimal("17.41"),
                service("1.0").upgradeDelta("DE", ITEMS, "  dhl express "));
    }

    @Test
    void defaultOrCheaperPick_costsNothingExtra() {
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"),
                new CjLogisticsOption("CJPacket Slow", new BigDecimal("3.10"), "15-25"));

        CjFreightQuoteService service = service("1.0");
        // Picking the default line itself: nothing to upgrade.
        assertEquals(new BigDecimal("0.00"), service.upgradeDelta("DE", ITEMS, "CJPacket Ordinary"));
        // Picking a CHEAPER line never discounts below the flat promise (max 0).
        assertEquals(new BigDecimal("0.00"), service.upgradeDelta("DE", ITEMS, "CJPacket Slow"));
    }

    @Test
    void unknownPick_fallsBackToZero_neverAnError() {
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"));

        assertEquals(new BigDecimal("0.00"),
                service("1.0").upgradeDelta("DE", ITEMS, "Carrier Pigeon"));
    }

    @Test
    void absentOrBlankPick_isZero_withoutTouchingCj() {
        CjFreightQuoteService service = service("1.0");

        assertEquals(new BigDecimal("0.00"), service.upgradeDelta("DE", ITEMS, null));
        assertEquals(new BigDecimal("0.00"), service.upgradeDelta("DE", ITEMS, "  "));
        verifyNoMoreInteractions(facade, lineResolver);
    }

    @Test
    void noOfferedOptions_isZero() {
        // CJ outage / no creds / no lane — options() answers empty and the delta is 0.
        when(lineResolver.resolveVid(101)).thenReturn("vid-101");
        when(facade.quoteLogisticsOptions(anyString(), any())).thenReturn(List.of());

        assertEquals(new BigDecimal("0.00"),
                service("1.0").upgradeDelta("DE", ITEMS, "DHL Express"));
    }

    @Test
    void unpriceableSelectedLine_yieldsZero() {
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"),
                new CjLogisticsOption("DHL Express", null, "3-5"));

        assertEquals(new BigDecimal("0.00"),
                service("1.0").upgradeDelta("DE", ITEMS, "DHL Express"));
    }

    @Test
    void unpriceableDefaultLine_yieldsZero() {
        // Nothing to upgrade from ⇒ nothing extra to charge.
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", null, "8-12"),
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"));

        assertEquals(new BigDecimal("0.00"),
                service("1.0").upgradeDelta("DE", ITEMS, "DHL Express"));
    }

    @Test
    void deltaComputesOnPostFxAmounts() {
        // Wave 24 seam: options() converts USD → store currency BEFORE the delta —
        // 23.40×0.5=11.70, 5.99×0.5=3.00 (HALF_UP) → delta 8.70, not 17.41×0.5=8.71.
        cjOffers("CJPacket Ordinary",
                new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12"),
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"));

        assertEquals(new BigDecimal("8.70"),
                service("0.5").upgradeDelta("DE", ITEMS, "DHL Express"));
    }

    @Test
    void pairwiseDelta_forTheChooserLabels() {
        CjLogisticsOption def = new CjLogisticsOption("CJPacket Ordinary", new BigDecimal("5.99"), "8-12");

        // Priced line → its surcharge over the default; the default line itself → 0.00.
        assertEquals(new BigDecimal("17.41"), CjFreightQuoteService.delta(
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"), def));
        assertEquals(new BigDecimal("0.00"), CjFreightQuoteService.delta(def, def));
        // Unpriceable line → null (the chooser shows no label rather than a fake 0).
        assertNull(CjFreightQuoteService.delta(
                new CjLogisticsOption("DHL Express", null, "3-5"), def));
        // No priceable default → 0.00 on priced lines (nothing to upgrade from).
        assertEquals(new BigDecimal("0.00"), CjFreightQuoteService.delta(
                new CjLogisticsOption("DHL Express", new BigDecimal("23.40"), "3-5"), null));
    }
}
