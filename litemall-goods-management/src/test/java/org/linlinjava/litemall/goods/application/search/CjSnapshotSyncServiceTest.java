package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.infrastructure.acl.cache.CjRawCacheRepository;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-12 sync facts: toRow persists the RAW USD cost (sell_price) alongside the
 * ×margin retail, and SyncResult.insertedPids carries exactly the genuinely-new pids
 * (the inventory flow's NEW_ARRIVAL source).
 */
public class CjSnapshotSyncServiceTest {

    private CJProductService cjProductService;
    private LitemallCjProductService cjProductStore;
    private CJDropshippingConfig config;
    private CjSnapshotSyncService service;

    @BeforeEach
    public void setUp() {
        cjProductService = mock(CJProductService.class);
        cjProductStore = mock(LitemallCjProductService.class);
        CjRawCacheRepository rawCache = mock(CjRawCacheRepository.class);
        CjCategoryTreeSyncService treeSync = mock(CjCategoryTreeSyncService.class);
        config = new CJDropshippingConfig(); // real @Data config: margin 1.25, no currency factor
        config.setEnabled(true);
        config.setCatalogTargets(null); // plain fetchProductList path
        service = new CjSnapshotSyncService(cjProductService, cjProductStore, config,
                new ObjectMapper(), rawCache, treeSync, new CjPricing(config));
    }

    private static CJProduct product(String pid, String sellPrice) {
        CJProduct p = new CJProduct();
        p.setPid(pid);
        p.setProductNameEn("Product " + pid);
        p.setSellPrice(sellPrice);
        return p;
    }

    private void stubFetch(CJProduct... products) {
        CJProductData data = new CJProductData();
        data.setList(List.of(products));
        CJProductDataResponse response = new CJProductDataResponse();
        response.setData(data);
        when(cjProductService.fetchProductList()).thenReturn(response);
    }

    @Test
    public void toRowPersistsRawCostAndMarkedUpRetail() {
        stubFetch(product("new-pid", "10.00 -- 25.00"));
        when(cjProductStore.queryLivePids()).thenReturn(List.of());

        service.syncAll();

        ArgumentCaptor<LitemallCjProduct> row = ArgumentCaptor.forClass(LitemallCjProduct.class);
        verify(cjProductStore).upsert(row.capture());
        assertEquals(new BigDecimal("10.00"), row.getValue().getSellPrice(), "raw USD cost, range lower bound");
        assertEquals(new BigDecimal("12.50"), row.getValue().getPrice(), "retail = cost × 1.25, no ×7.2");
        assertTrue(row.getValue().getVariantsJson().contains("\"variant_sell_price\":10.0"),
                "shallow variant carries the cost: " + row.getValue().getVariantsJson());
    }

    @Test
    public void unparseableSellPriceLeavesCostAndPriceNull() {
        stubFetch(product("odd-pid", "call us"));
        when(cjProductStore.queryLivePids()).thenReturn(List.of());

        service.syncAll();

        ArgumentCaptor<LitemallCjProduct> row = ArgumentCaptor.forClass(LitemallCjProduct.class);
        verify(cjProductStore).upsert(row.capture());
        assertNull(row.getValue().getSellPrice());
        assertNull(row.getValue().getPrice());
    }

    @Test
    public void insertedPidsListsOnlyGenuinelyNewPids() {
        stubFetch(product("brand-new", "5"), product("already-known", "6"));
        when(cjProductStore.queryLivePids()).thenReturn(List.of("already-known"));

        CjSnapshotSyncService.SyncResult result = service.syncAll();

        assertEquals(List.of("brand-new"), result.insertedPids());
        assertEquals(1, result.inserted());
        assertEquals(1, result.updated());
        assertTrue(result.livePids().containsAll(List.of("brand-new", "already-known")));
        Mockito.verify(cjProductStore, Mockito.never()).softDelete(any());
    }
}
