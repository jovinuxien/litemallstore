package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.application.inventoryflow.InventoryFlowGateway;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvariant.CJProductVariantData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 25.1 at the enrichment seam: CJ's {@code variantImage} lands in {@code variants_json} as
 * {@code variant_image} — but ONLY when usable (plain URL on a /_cdn-covered host, column-width
 * safe); anything else leaves the key absent so promote falls back to the main photo. Rides the
 * same mocked-CJ harness as {@link CjEnrichmentRepriceTest}.
 *
 * <p>Also the supplier-junk gate (prod finding 2026-08-10: CJ delivers the literal string
 * {@code "{}"} for supplierName/supplierId): junk reads as ABSENT at persistence — no supplier
 * columns, no "Supplier" display attribute.
 */
public class CjEnrichmentVariantImageTest {

    private static final String CDN_IMG = "https://cf.cjdropshipping.com/variant/red.jpg";

    private CJProductService cjProductService;
    private LitemallCjProductService cjProductStore;
    private CjProductPromotionService promotionService;
    private CjDetailEnrichmentService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                promotionService, reindexService, config, objectMapper,
                new CjPricing(config), gateway);
    }

    private LitemallCjProduct enrich(CJProductDetailData detail) {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-1");
        when(cjProductStore.findByPid("pid-1")).thenReturn(row);
        when(cjProductService.getProductDetail("pid-1")).thenReturn(detail);
        when(cjProductService.getProductComments(anyString(), anyInt(), anyInt())).thenReturn(null);
        when(promotionService.promote(any())).thenReturn(42);

        service.enrichByPid("pid-1");

        ArgumentCaptor<LitemallCjProduct> captor = ArgumentCaptor.forClass(LitemallCjProduct.class);
        verify(cjProductStore).enrich(captor.capture());
        return captor.getValue();
    }

    private static CJProductVariantData variant(String vid, String image) {
        CJProductVariantData v = new CJProductVariantData();
        v.setVid(vid);
        v.setVariantSku("SKU-" + vid);
        v.setVariantImage(image);
        return v;
    }

    private JsonNode variantsOf(LitemallCjProduct persisted) throws Exception {
        return objectMapper.readTree(persisted.getVariantsJson());
    }

    @Test
    public void usableVariantImageIsPersistedIntoVariantsJson() throws Exception {
        CJProductDetailData detail = new CJProductDetailData();
        detail.setVariants(List.of(variant("v1", CDN_IMG), variant("v2", null)));

        JsonNode variants = variantsOf(enrich(detail));
        assertEquals(CDN_IMG, variants.get(0).get("variant_image").asText());
        assertFalse(variants.get(1).has("variant_image"), "absent image -> key absent, not null/blank");
    }

    @Test
    public void jsonArrayShapedVariantImageIsFlattenedToItsFirstUrl() throws Exception {
        // CJ stringifies some image fields as JSON arrays (same as productImage) — tolerate it.
        CJProductDetailData detail = new CJProductDetailData();
        detail.setVariants(List.of(variant("v1", "[\"" + CDN_IMG + "\",\"https://cf.cjdropshipping.com/2.jpg\"]")));

        JsonNode variants = variantsOf(enrich(detail));
        assertEquals(CDN_IMG, variants.get(0).get("variant_image").asText());
    }

    @Test
    public void unusableVariantImagesAreDropped() throws Exception {
        CJProductDetailData detail = new CJProductDetailData();
        detail.setVariants(List.of(
                variant("v1", "https://cc-west-usa.oss-us-west-1.aliyuncs.com/x.jpg"), // uncovered host
                variant("v2", "https://cf.cjdropshipping.com/" + "a".repeat(120) + ".jpg"), // > 125 chars
                variant("v3", "   ")));

        JsonNode variants = variantsOf(enrich(detail));
        for (JsonNode v : variants) {
            assertFalse(v.has("variant_image"), "unusable image must read as absent: " + v);
        }
    }

    @Test
    public void junkSupplierFieldsReadAsAbsent() {
        CJProductDetailData detail = new CJProductDetailData();
        detail.setSupplierId("{}");
        detail.setSupplierName("{}");

        LitemallCjProduct persisted = enrich(detail);
        assertNull(persisted.getSupplierId(), "literal \"{}\" must never land on the row");
        assertNull(persisted.getSupplierName());
        assertFalse(persisted.getAttributesJson().contains("Supplier"),
                "junk supplier must not render as a PDP attribute");
    }

    @Test
    public void realSupplierFieldsStillPersistAndRenderAsAttribute() {
        CJProductDetailData detail = new CJProductDetailData();
        detail.setSupplierId("SUP-9");
        detail.setSupplierName("Wenling Chengdong Jiuwei Shoe and Hat Business");

        LitemallCjProduct persisted = enrich(detail);
        assertEquals("SUP-9", persisted.getSupplierId());
        assertEquals("Wenling Chengdong Jiuwei Shoe and Hat Business", persisted.getSupplierName());
        assertTrue(persisted.getAttributesJson().contains("Wenling Chengdong Jiuwei Shoe and Hat Business"));
    }

    @Test
    public void nullIshSupplierLiteralsAlsoReadAsAbsent() {
        CJProductDetailData detail = new CJProductDetailData();
        detail.setSupplierId("null");
        detail.setSupplierName("   ");

        LitemallCjProduct persisted = enrich(detail);
        assertNull(persisted.getSupplierId());
        assertNull(persisted.getSupplierName());
    }
}
