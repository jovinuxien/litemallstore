package org.linlinjava.litemall.goods.application.insight;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 26 Phase 1b. The report's only real risk is arithmetic that lies by omission: EU coverage
 * grows with the enrichment rotation, so a survival share taken over the whole on-sale count would
 * read "3% of our catalogue has EU stock" when the truth is "we have probed 3% of it". Percentages
 * are therefore over probedCount, and an unprobed category yields null — never 0.
 */
public class EuSourcingServiceTest {

    private LitemallCjProductService store;
    private EuSourcingService service;

    @BeforeEach
    public void setUp() {
        store = mock(LitemallCjProductService.class);
        CJDropshippingConfig config = new CJDropshippingConfig();
        service = new EuSourcingService(store, config);
    }

    private Map<String, Object> row(int id, String name, long onSale, long probed, long eu, Double avg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryId", id);
        m.put("name", name);
        m.put("onSaleCount", onSale);
        m.put("probedCount", probed);
        m.put("euStockedCount", eu);
        m.put("avgEuStock", avg);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> categories(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("categories");
    }

    @Test
    public void survivalIsComputedOverProbedNotOverOnSale() {
        // 1000 on sale, only 100 probed, 25 of those EU-stocked. The honest answer is 25%, not 2.5%.
        when(store.euSurvivalByRoot()).thenReturn(List.of(row(1, "Home Improvement", 1000, 100, 25, 4.0)));

        Map<String, Object> cat = categories(service.report()).get(0);

        assertEquals(25.0, cat.get("euSurvivalPct"));
        assertEquals(100L, cat.get("probedCount"), "the denominator must ship with the number");
        assertEquals(1000L, cat.get("onSaleCount"));
    }

    @Test
    public void anUnprobedCategoryIsNullNotZero() {
        when(store.euSurvivalByRoot()).thenReturn(List.of(row(2, "Bags & Shoes", 500, 0, 0, null)));

        Map<String, Object> cat = categories(service.report()).get(0);

        assertNull(cat.get("euSurvivalPct"), "0% would claim we looked and found nothing");
        assertNull(cat.get("euStockedCount"), "absent is not zero");
        assertEquals(0L, cat.get("probedCount"));
    }

    @Test
    public void probedWithNoEuStockIsGenuinelyZero() {
        when(store.euSurvivalByRoot()).thenReturn(List.of(row(3, "Jewelry", 200, 200, 0, null)));

        Map<String, Object> cat = categories(service.report()).get(0);

        assertEquals(0.0, cat.get("euSurvivalPct"), "probed and empty IS a measured zero");
        assertEquals(0L, cat.get("euStockedCount"));
    }

    /** Unknowns must not outrank measured results, in either direction. */
    @Test
    public void unprobedCategoriesSortLast() {
        when(store.euSurvivalByRoot()).thenReturn(List.of(
                row(1, "unprobed", 900, 0, 0, null),
                row(2, "weak", 100, 100, 2, 1.0),
                row(3, "strong", 100, 100, 40, 6.0)));

        List<Map<String, Object>> cats = categories(service.report());

        assertEquals("strong", cats.get(0).get("name"));
        assertEquals("weak", cats.get(1).get("name"));
        assertEquals("unprobed", cats.get(2).get("name"));
    }

    @Test
    public void totalsCarryCoverageSoTheReportCannotOverclaim() {
        when(store.euSurvivalByRoot()).thenReturn(List.of(
                row(1, "a", 800, 200, 50, 3.0),
                row(2, "b", 200, 0, 0, null)));

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) service.report().get("totals");

        assertEquals(1000L, totals.get("onSaleCount"));
        assertEquals(200L, totals.get("probedCount"));
        assertEquals(25.0, totals.get("euSurvivalPct"), "50 of 200 probed");
        assertEquals(20.0, totals.get("coveragePct"), "200 of 1000 on-sale goods probed");
    }

    @Test
    public void theConfiguredEuCountriesAreReported() {
        when(store.euSurvivalByRoot()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<String> countries = (List<String>) service.report().get("countries");

        assertTrue(countries.contains("DE"), "DE is CJ's only EU warehouse (Phase 1a)");
        assertTrue(!countries.contains("GB"), "GB is post-Brexit and does not serve EU customers");
    }
}
