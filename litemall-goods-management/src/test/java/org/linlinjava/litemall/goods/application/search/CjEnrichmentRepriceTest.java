package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.application.inventoryflow.InventoryFlowGateway;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-12 tail-repricing law (prod finding, 2026-07-28): the nightly plan only rotates a
 * slice of the catalog, so enrichment IS the repricing path for the long tail. When detail
 * enrichment captures a cost it must recompute the snapshot retail with it — otherwise an
 * on-demand-enriched product keeps its pre-Wave-12 ×14.4 price alongside a fresh cost.
 */
public class CjEnrichmentRepriceTest {

    private CJProductService cjProductService;
    private LitemallCjProductService cjProductStore;
    private CjProductPromotionService promotionService;
    private CjDetailEnrichmentService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        cjProductService = mock(CJProductService.class);
        cjProductStore = mock(LitemallCjProductService.class);
        promotionService = mock(CjProductPromotionService.class);
        SearchReindexService reindexService = mock(SearchReindexService.class);
        ObjectProvider<InventoryFlowGateway> gateway = mock(ObjectProvider.class);
        CJDropshippingConfig config = new CJDropshippingConfig();
        config.setEnabled(true);
        service = new CjDetailEnrichmentService(cjProductService, cjProductStore,
                promotionService, reindexService, config, new ObjectMapper(),
                new CjPricing(config), gateway);
    }

    @Test
    public void enrichmentRepricesTheSnapshotFromTheDetailCost() {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-1");
        row.setPrice(new BigDecimal("149.76"));  // pre-Wave-12 ×14.4 price
        row.setSellPrice(null);                  // cost never captured by a sync
        when(cjProductStore.findByPid("pid-1")).thenReturn(row);

        CJProductDetailData detail = new CJProductDetailData();
        detail.setSellPrice(10.40);
        when(cjProductService.getProductDetail("pid-1")).thenReturn(detail);
        when(cjProductService.getProductComments(anyString(), anyInt(), anyInt())).thenReturn(null);
        when(promotionService.promote(any())).thenReturn(42);

        service.enrichByPid("pid-1");

        ArgumentCaptor<LitemallCjProduct> captor = ArgumentCaptor.forClass(LitemallCjProduct.class);
        verify(cjProductStore).enrich(captor.capture());
        assertEquals(new BigDecimal("10.40"), captor.getValue().getSellPrice());
        assertEquals(new BigDecimal("13.00"), captor.getValue().getPrice(),
                "enrichment must reprice: 10.40 × 1.25, not the stale 149.76");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void fxConvertsDetailCostAtLanding() {
        // Wave 24: the enrichment seam lands the detail cost × fx-usd-eur; retail derives
        // from the converted cost so the margin ratio is untouched.
        CJDropshippingConfig config = new CJDropshippingConfig();
        config.setEnabled(true);
        service = new CjDetailEnrichmentService(cjProductService, cjProductStore,
                promotionService, mock(SearchReindexService.class), config, new ObjectMapper(),
                new CjPricing(config, null, new BigDecimal("0.5")),
                (ObjectProvider<InventoryFlowGateway>) mock(ObjectProvider.class));

        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-2");
        when(cjProductStore.findByPid("pid-2")).thenReturn(row);

        CJProductDetailData detail = new CJProductDetailData();
        detail.setSellPrice(10.40);
        when(cjProductService.getProductDetail("pid-2")).thenReturn(detail);
        when(cjProductService.getProductComments(anyString(), anyInt(), anyInt())).thenReturn(null);
        when(promotionService.promote(any())).thenReturn(42);

        service.enrichByPid("pid-2");

        ArgumentCaptor<LitemallCjProduct> captor = ArgumentCaptor.forClass(LitemallCjProduct.class);
        verify(cjProductStore).enrich(captor.capture());
        assertEquals(new BigDecimal("5.20"), captor.getValue().getSellPrice(), "detail cost lands × fx");
        assertEquals(new BigDecimal("6.50"), captor.getValue().getPrice(), "retail = converted cost × 1.25");
    }
}
