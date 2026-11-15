package org.linlinjava.litemall.gatewayapi.web.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class CurrencyControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), ZoneOffset.UTC);

    private static EcbRateService rates(boolean enabled, String body) {
        return new EcbRateService(enabled,
                () -> body == null ? CompletableFuture.failedFuture(new IllegalStateException("down"))
                        : CompletableFuture.completedFuture(body),
                Duration.ofHours(6), 7, CLOCK);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> res) {
        assertEquals(0, res.getBody().get("errno"));
        return (Map<String, Object>) res.getBody().get("data");
    }

    private static CurrencyController all() {
        return new CurrencyController("EUR,SEK,DKK,NOK,GBP", rates(true, EcbRatesTest.SAMPLE));
    }

    @Test
    void eachMarketCountryGetsItsOwnCurrency() {
        assertEquals("SEK", data(all().currency("SE")).get("suggested"));
        assertEquals("DKK", data(all().currency("DK")).get("suggested"));
        assertEquals("NOK", data(all().currency("NO")).get("suggested"));
        assertEquals("GBP", data(all().currency("GB")).get("suggested"));
    }

    @Test
    void euroCountriesAndEveryoneElseGetEuros() {
        for (String c : new String[] {"DE", "FR", "FI", "US", "PL", "XX", "T1", "", "  ", "sweden"}) {
            assertEquals("EUR", data(all().currency(c)).get("suggested"), c);
        }
        Map<String, Object> noHeader = data(all().currency(null));
        assertEquals("EUR", noHeader.get("suggested"));
        assertNull(noHeader.get("country"));
    }

    @Test
    void countryHeaderIsNormalised() {
        Map<String, Object> d = data(all().currency(" se "));
        assertEquals("SE", d.get("country"));
        assertEquals("SEK", d.get("suggested"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void carriesTheEcbRatesAndTheirDate() {
        Map<String, Object> d = data(all().currency("SE"));
        assertEquals(List.of("EUR", "SEK", "DKK", "NOK", "GBP"), d.get("enabled"));
        Map<String, BigDecimal> rates = (Map<String, BigDecimal>) d.get("rates");
        assertEquals(new BigDecimal("11.0125"), rates.get("SEK"));
        assertEquals(4, rates.size());
        assertEquals("2026-11-13", d.get("asOf"));
    }

    @Test
    void theAnswerIsNeverCacheable() {
        assertTrue(all().currency("SE").getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultConfigIsOffEvenForASwedishVisitor() {
        CurrencyController off = new CurrencyController("EUR", rates(false, EcbRatesTest.SAMPLE));
        Map<String, Object> d = data(off.currency("SE"));
        assertEquals("EUR", d.get("suggested"));
        assertEquals(List.of("EUR"), d.get("enabled"));
        assertTrue(((Map<String, BigDecimal>) d.get("rates")).isEmpty());
        assertNull(d.get("asOf"));
    }

    @Test
    void aCurrencyTheOperatorDidNotListIsNotSuggested() {
        CurrencyController c = new CurrencyController("EUR,SEK", rates(true, EcbRatesTest.SAMPLE));
        assertEquals("SEK", data(c.currency("SE")).get("suggested"));
        Map<String, Object> dk = data(c.currency("DK"));
        assertEquals("EUR", dk.get("suggested"));
        assertEquals(List.of("EUR", "SEK"), dk.get("enabled"));
    }

    @Test
    void withoutRatesEverythingFallsBackToEuros() {
        CurrencyController c = new CurrencyController("EUR,SEK,DKK,NOK,GBP", rates(true, null));
        Map<String, Object> d = data(c.currency("SE"));
        assertEquals("EUR", d.get("suggested"));
        assertEquals(List.of("EUR"), d.get("enabled"));
    }

    @Test
    void aCurrencyMissingFromTheEcbFileIsDroppedAlone() {
        CurrencyController c = new CurrencyController("EUR,SEK,DKK,NOK,GBP",
                rates(true, EcbRatesTest.SAMPLE.replace("<Cube currency='NOK' rate='11.7180'/>", "")));
        assertEquals("EUR", data(c.currency("NO")).get("suggested"));
        assertEquals(List.of("EUR", "SEK", "DKK", "GBP"), data(c.currency("NO")).get("enabled"));
    }

    @Test
    void configParsingIsForgiving() {
        assertEquals(List.of("EUR"), CurrencyController.parseCurrencies(null));
        assertEquals(List.of("EUR"), CurrencyController.parseCurrencies(""));
        assertEquals(List.of("EUR", "SEK", "DKK"), CurrencyController.parseCurrencies(" sek, DKK ,sek, xyz, eur"));
    }
}
