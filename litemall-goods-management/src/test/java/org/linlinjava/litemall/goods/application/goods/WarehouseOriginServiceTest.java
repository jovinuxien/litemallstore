package org.linlinjava.litemall.goods.application.goods;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 28 §3.1: an origin row exists ONLY for a goods whose last probe measured EU stock in a
 * configured EU warehouse country. Every other state — unknown goods, local goods, no snapshot,
 * never probed, probed-zero, stock outside the EU set — is ABSENT, never "CN", never null.
 */
public class WarehouseOriginServiceTest {

    private LitemallGoodsService goodsRows;
    private LitemallCjProductService cjRows;
    private WarehouseOriginService service;

    @BeforeEach
    void setUp() {
        goodsRows = mock(LitemallGoodsService.class);
        cjRows = mock(LitemallCjProductService.class);
        CJDropshippingConfig config = new CJDropshippingConfig();
        config.setEuWarehouseCountries(new java.util.LinkedHashSet<>(List.of("DE")));
        service = new WarehouseOriginService(goodsRows, cjRows, config);
    }

    private void goods(int id, String pid) {
        LitemallGoods g = new LitemallGoods();
        g.setId(id);
        g.setCjPid(pid);
        when(goodsRows.findById(id)).thenReturn(g);
    }

    private void snapshot(String pid, Integer euUnits, String countries) {
        LitemallCjProduct p = new LitemallCjProduct();
        p.setPid(pid);
        p.setEuStockNum(euUnits);
        p.setWarehouseCountries(countries);
        when(cjRows.findByPid(pid)).thenReturn(p);
    }

    @Test
    void aMeasuredGoodsGetsItsEuCountry() {
        goods(10, "pid-10");
        snapshot("pid-10", 230, "CN,DE");

        List<WarehouseOriginService.Origin> rows = service.originsOf(List.of(10));

        assertEquals(1, rows.size());
        assertEquals(10, rows.get(0).goodsId());
        assertEquals("DE", rows.get(0).originCountry());
    }

    /** NULL (never probed) and 0 (probed, none) collapse to absence — the eu_flag rationale. */
    @Test
    void unprobedAndProbedZeroAreAbsent() {
        goods(11, "pid-11");
        snapshot("pid-11", null, "CN");
        goods(12, "pid-12");
        snapshot("pid-12", 0, "CN,DE");

        assertTrue(service.originsOf(List.of(11, 12)).isEmpty());
        assertTrue(service.measuredEuStock(11).isEmpty());
        assertTrue(service.measuredEuStock(12).isEmpty());
    }

    @Test
    void unknownLocalAndSnapshotlessGoodsAreAbsent() {
        when(goodsRows.findById(anyInt())).thenReturn(null);
        goods(13, null);            // local goods: no CJ pid
        goods(14, "pid-14");        // CJ goods whose snapshot row is gone
        when(cjRows.findByPid("pid-14")).thenReturn(null);

        assertTrue(service.originsOf(List.of(99, 13, 14)).isEmpty());
    }

    /**
     * Units measured but no configured EU country in the reading: the PDP may still badge the
     * units, but an ORIGIN needs a country we measured, so the row is withheld (fail closed).
     */
    @Test
    void stockWithoutARecognisedEuCountryYieldsNoOriginRow() {
        goods(15, "pid-15");
        snapshot("pid-15", 5, "GB, us");

        assertTrue(service.originsOf(List.of(15)).isEmpty());
        Optional<WarehouseOriginService.EuReading> reading = service.measuredEuStock(15);
        assertTrue(reading.isPresent());
        assertEquals(5, reading.get().units());
        assertTrue(reading.get().countries().isEmpty());
    }

    /** The reading names only configured EU countries, upper-cased, deduplicated, in order seen. */
    @Test
    void countriesAreFilteredToTheConfiguredEuSet() {
        goods(16, "pid-16");
        snapshot("pid-16", 20, " cn , de,DE, gb ");

        assertEquals(List.of("DE"), service.measuredEuStock(16).get().countries());
    }

    /** One id blowing up must not hide the others — an origin note can never break a checkout. */
    @Test
    void aFailingLookupSkipsThatRowOnly() {
        goods(17, "pid-17");
        snapshot("pid-17", 9, "DE");
        when(goodsRows.findById(18)).thenThrow(new IllegalStateException("db hiccup"));

        List<WarehouseOriginService.Origin> rows = service.originsOf(List.of(18, 17));

        assertEquals(1, rows.size());
        assertEquals(17, rows.get(0).goodsId());
    }

    @Test
    void duplicateIdsAreReadOnce() {
        goods(19, "pid-19");
        snapshot("pid-19", 1, "DE");

        List<WarehouseOriginService.Origin> rows = service.originsOf(List.of(19, 19, 19));

        assertEquals(1, rows.size());
        verify(goodsRows).findById(19);
    }

    @Test
    void nullAndNonPositiveIdsAreAbsentWithoutAQuery() {
        assertTrue(service.measuredEuStock(null).isEmpty());
        assertTrue(service.measuredEuStock(0).isEmpty());
        assertTrue(service.originsOf(null).isEmpty());
        verify(goodsRows, org.mockito.Mockito.never()).findById(anyInt());
    }

    @Test
    void parseIdsIsTolerantOrderedAndDeduplicated() {
        assertEquals(List.of(3, 1, 2), WarehouseOriginService.parseIds(" 3, 1 ,,x,-4,0,2,1,3.5 "));
        assertTrue(WarehouseOriginService.parseIds(null).isEmpty());
        assertTrue(WarehouseOriginService.parseIds("  ").isEmpty());
        assertTrue(WarehouseOriginService.parseIds("a,b").isEmpty());
        assertFalse(WarehouseOriginService.parseIds("7").isEmpty());
    }
}
