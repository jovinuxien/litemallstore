package org.linlinjava.litemall.gatewayapi.web.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** The ECB daily file → rates. No {@code @Nested} (surefire here discovers none). */
class EcbRatesTest {

    /** Shape of the real eurofxref-daily.xml: three nested Cube levels, gesmes envelope. */
    static final String SAMPLE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<gesmes:Envelope xmlns:gesmes=\"http://www.gesmes.org/xml/2002-08-01\" xmlns=\"http://www.ecb.int/vocabulary/2002-08-01/eurofxref\">\n"
            + "<gesmes:subject>Reference rates</gesmes:subject>\n"
            + "<gesmes:Sender><gesmes:name>European Central Bank</gesmes:name></gesmes:Sender>\n"
            + "<Cube><Cube time='2026-11-13'>\n"
            + "<Cube currency='USD' rate='1.1555'/>\n"
            + "<Cube currency='DKK' rate='7.4641'/>\n"
            + "<Cube currency='GBP' rate='0.87850'/>\n"
            + "<Cube currency='SEK' rate='11.0125'/>\n"
            + "<Cube currency='NOK' rate='11.7180'/>\n"
            + "</Cube></Cube>\n</gesmes:Envelope>";

    @Test
    void readsTheDateAndEveryListedRate() {
        EcbRates r = EcbRates.parse(SAMPLE);
        assertEquals(LocalDate.of(2026, 11, 13), r.asOf());
        assertEquals(new BigDecimal("11.0125"), r.rate("SEK"));
        assertEquals(new BigDecimal("7.4641"), r.rate("DKK"));
        assertEquals(new BigDecimal("11.7180"), r.rate("NOK"));
        assertEquals(new BigDecimal("0.87850"), r.rate("GBP"));
        assertNull(r.rate("PLN"));
    }

    @Test
    void oneUnreadableOrNonPositiveRateIsDroppedNotGuessed() {
        EcbRates r = EcbRates.parse(SAMPLE.replace("rate='11.0125'", "rate='n/a'").replace("rate='7.4641'", "rate='0'"));
        assertNull(r.rate("SEK"));
        assertNull(r.rate("DKK"));
        assertEquals(new BigDecimal("11.7180"), r.rate("NOK"));
    }

    @Test
    void aDocumentWithoutDatedRatesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> EcbRates.parse(""));
        assertThrows(IllegalArgumentException.class, () -> EcbRates.parse("<html><body>maintenance</body></html>"));
        assertThrows(IllegalArgumentException.class, () -> EcbRates.parse("<Cube><Cube time='2026-11-13'></Cube></Cube>"));
        assertThrows(IllegalArgumentException.class, () -> EcbRates.parse("<Cube><Cube currency='SEK' rate='11'/></Cube>"));
        assertThrows(IllegalArgumentException.class, () -> EcbRates.parse("not xml at all"));
    }

    @Test
    void doctypeDeclarationsAreRefused() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<Cube><Cube time='2026-11-13'><Cube currency='SEK' rate='11'/></Cube></Cube>";
        assertThrows(IllegalArgumentException.class, () -> EcbRates.parse(xxe));
    }
}
