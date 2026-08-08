package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V52 delivery-option chooser: the selection rule shared by checkout preview and
 * pay-time placement — customer preference when offered, else configured default,
 * else cheapest. Pure logic, no CJ call.
 */
class CjLogisticsChoiceTest {

    private CjDropshipOrderFacadeImpl facade(String defaultLine) {
        return new CjDropshipOrderFacadeImpl(null, null, "CN", defaultLine, false, 0, "", "orders@test");
    }

    private static CjLogisticsOption line(String name, String price) {
        return new CjLogisticsOption(name, price == null ? null : new BigDecimal(price), "8-12");
    }

    private final List<CjLogisticsOption> offered = List.of(
            line("CJPacket Ordinary", "5.10"),
            line("CJPacket Sensitive", "6.40"),
            line("DHL Express", "22.00"));

    @Test
    void customerPreferenceWinsWhenOffered() {
        CjLogisticsOption chosen = facade("CJPacket Ordinary")
                .chooseLogistics(offered, "DHL Express");
        assertEquals("DHL Express", chosen.getLogisticName());
    }

    @Test
    void preferenceMatchIsCaseInsensitiveAndTrimmed() {
        CjLogisticsOption chosen = facade("CJPacket Ordinary")
                .chooseLogistics(offered, "  dhl express ");
        assertEquals("DHL Express", chosen.getLogisticName());
    }

    @Test
    void unofferedPreferenceFallsBackToConfiguredDefault() {
        CjLogisticsOption chosen = facade("CJPacket Sensitive")
                .chooseLogistics(offered, "USPS Priority");
        assertEquals("CJPacket Sensitive", chosen.getLogisticName());
    }

    @Test
    void noPreferenceUsesConfiguredDefault() {
        CjLogisticsOption chosen = facade("CJPacket Sensitive").chooseLogistics(offered, null);
        assertEquals("CJPacket Sensitive", chosen.getLogisticName());
    }

    @Test
    void missingDefaultFallsBackToCheapest() {
        CjLogisticsOption chosen = facade("Nonexistent Line").chooseLogistics(offered, "");
        assertEquals("CJPacket Ordinary", chosen.getLogisticName());
    }

    @Test
    void nullPricedLineNeverBeatsAPricedOne() {
        List<CjLogisticsOption> withNullPrice = List.of(line("No Price Line", null), line("Cheap", "1.00"));
        CjLogisticsOption chosen = facade("Nonexistent Line").chooseLogistics(withNullPrice, null);
        assertEquals("Cheap", chosen.getLogisticName());
    }

    @Test
    void emptyOfferListYieldsNull() {
        assertNull(facade("CJPacket Ordinary").chooseLogistics(List.of(), "DHL Express"));
        assertNull(facade("CJPacket Ordinary").chooseLogistics(null, null));
    }
}
