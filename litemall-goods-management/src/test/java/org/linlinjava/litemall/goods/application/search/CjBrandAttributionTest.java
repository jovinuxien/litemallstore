package org.linlinjava.litemall.goods.application.search;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.dao.LitemallCategoryMapper;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsAttributeMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsSpecificationMapper;
import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.goods.application.attribution.AttributionProvider;
import org.linlinjava.litemall.goods.application.attribution.CjSupplierAttributionProvider;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.CjProductToNativeAdapter;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.NativeGoodsAggregate;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-25 attribution rules at the promote seam: provider rows are upserted keyed
 * (source, external_id) with the display-curation gate DOWN; an existing row's (possibly
 * admin-curated) name is never touched; resolution prefers providers over the legacy
 * name-keyed path.
 */
public class CjBrandAttributionTest {

    private LitemallCjLinkageMapper linkageMapper;
    private LitemallBrandMapper brandMapper;
    private CjProductPromotionService service;

    @BeforeEach
    public void setUp() {
        linkageMapper = mock(LitemallCjLinkageMapper.class);
        brandMapper = mock(LitemallBrandMapper.class);
        CJDropshippingConfig config = new CJDropshippingConfig();
        service = new CjProductPromotionService(
                linkageMapper, mock(LitemallGoodsMapper.class),
                mock(LitemallGoodsProductMapper.class), mock(LitemallGoodsAttributeMapper.class),
                mock(LitemallGoodsSpecificationMapper.class), mock(LitemallCategoryMapper.class),
                brandMapper, mock(LitemallSeckillMapper.class),
                mock(CjProductToNativeAdapter.class), mock(CjCategoryTreeSyncService.class),
                config, new CjPricing(config, mock(CategoryMarginResolver.class)),
                new LitemallGoodsProperties(), mock(CategoryMarginResolver.class),
                mock(org.linlinjava.litemall.db.service.LitemallCjProductService.class),
                List.of(new CjSupplierAttributionProvider()),
                mock(PlatformTransactionManager.class));
    }

    private static LitemallCjProduct snapshot(String supplierId, String supplierName) {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-1");
        row.setSupplierId(supplierId);
        row.setSupplierName(supplierName);
        return row;
    }

    @Test
    public void newSupplierCreatesCurationGatedStoreRow() {
        when(linkageMapper.findBrandBySourceAndExternalId("cj-supplier", "SUP-9")).thenReturn(null);

        service.resolveAttributedBrandId(snapshot("SUP-9", "Wenling Chengdong Jiuwei Shoe and Hat Business"), null);

        ArgumentCaptor<LitemallBrand> captor = ArgumentCaptor.forClass(LitemallBrand.class);
        verify(brandMapper).insertSelective(captor.capture());
        LitemallBrand created = captor.getValue();
        assertEquals("cj-supplier", created.getSource());
        assertEquals("SUP-9", created.getExternalId());
        assertEquals((byte) 1, created.getKind());
        assertEquals(Boolean.FALSE, created.getDisplayEnabled()); // raw legal names never render
        assertEquals("Wenling Chengdong Jiuwei Shoe and Hat Business", created.getName());
    }

    @Test
    public void existingRowIsReusedAndItsNameNeverOverwritten() {
        LitemallBrand existing = new LitemallBrand();
        existing.setId(41);
        existing.setName("Curated Store Name");
        existing.setDeleted(Boolean.FALSE);
        when(linkageMapper.findBrandBySourceAndExternalId("cj-supplier", "SUP-9")).thenReturn(existing);

        Integer id = service.resolveAttributedBrandId(snapshot("SUP-9", "Raw Legal Entity Name"), null);

        assertEquals(41, id);
        verify(brandMapper, never()).insertSelective(any());
        verify(brandMapper, never()).updateByPrimaryKeySelective(any()); // not deleted → nothing written
    }

    @Test
    public void softDeletedRowIsRevivedInPlaceWithoutTouchingName() {
        LitemallBrand deleted = new LitemallBrand();
        deleted.setId(42);
        deleted.setName("Curated Store Name");
        deleted.setDeleted(Boolean.TRUE);
        when(linkageMapper.findBrandBySourceAndExternalId("cj-supplier", "SUP-9")).thenReturn(deleted);

        Integer id = service.resolveAttributedBrandId(snapshot("SUP-9", "Raw Legal Entity Name"), null);

        assertEquals(42, id);
        ArgumentCaptor<LitemallBrand> captor = ArgumentCaptor.forClass(LitemallBrand.class);
        verify(brandMapper).updateByPrimaryKeySelective(captor.capture());
        assertEquals(42, captor.getValue().getId());
        assertNull(captor.getValue().getName()); // selective revive — name untouched
        assertEquals(Boolean.FALSE, captor.getValue().getDeleted());
        verify(brandMapper, never()).insertSelective(any());
    }

    @Test
    public void noSupplierFallsBackToLegacyNameKeyedPath() {
        NativeGoodsAggregate.BrandRef legacy = new NativeGoodsAggregate.BrandRef("SomeBrand");
        when(linkageMapper.findCjBrandIdByName("SomeBrand")).thenReturn(7);

        Integer id = service.resolveAttributedBrandId(snapshot(null, null), legacy);

        assertEquals(7, id);
        verify(linkageMapper, never()).findBrandBySourceAndExternalId(any(), any());
    }

    @Test
    public void nothingToAttributeResolvesNull() {
        assertNull(service.resolveAttributedBrandId(snapshot(null, null), null));
        verify(brandMapper, never()).insertSelective(any());
    }

    @Test
    public void providerYieldsStoreAttributionOnlyWithSupplierId() {
        CjSupplierAttributionProvider provider = new CjSupplierAttributionProvider();
        assertNull(provider.resolve(null));
        assertNull(provider.resolve(snapshot(null, "Name Without Id")));
        AttributionProvider.Attribution att = provider.resolve(snapshot(" SUP-1 ", null));
        assertEquals(AttributionProvider.KIND_STORE, att.kind());
        assertEquals("SUP-1", att.externalId());
        assertEquals("SUP-1", att.name()); // name falls back to the id when CJ omits it
    }

    // ---- Wave 25.1 supplier-junk gate (prod finding 2026-08-10: CJ delivers the literal string
    // "{}" in supplier fields; UNIQUE(source, external_id) funneled every such product onto one
    // garbage row — prod 1046002). Junk must read as ABSENT: no row, no link. -------------------

    @Test
    public void junkSupplierIdYieldsNoAttributionNoRowNoLink() {
        for (String junk : new String[]{"{}", "[]", "null", "NULL", "undefined", "", "   ", "-", "..."}) {
            assertNull(service.resolveAttributedBrandId(snapshot(junk, "Some Real Looking Name"), null),
                    "junk supplierId must resolve nothing: \"" + junk + "\"");
        }
        verify(brandMapper, never()).insertSelective(any());
        verify(linkageMapper, never()).findBrandBySourceAndExternalId(any(), any());
    }

    @Test
    public void junkSupplierNameWithRealIdFallsBackToTheIdAsPlaceholderName() {
        when(linkageMapper.findBrandBySourceAndExternalId("cj-supplier", "SUP-9")).thenReturn(null);

        service.resolveAttributedBrandId(snapshot("SUP-9", "{}"), null);

        ArgumentCaptor<LitemallBrand> captor = ArgumentCaptor.forClass(LitemallBrand.class);
        verify(brandMapper).insertSelective(captor.capture());
        assertEquals("SUP-9", captor.getValue().getName(), "junk name -> id placeholder, never \"{}\"");
        assertEquals("SUP-9", captor.getValue().getExternalId());
        assertEquals(Boolean.FALSE, captor.getValue().getDisplayEnabled());
    }

    @Test
    public void junkStillFallsBackToTheLegacyPathWhenPresent() {
        NativeGoodsAggregate.BrandRef legacy = new NativeGoodsAggregate.BrandRef("SomeBrand");
        when(linkageMapper.findCjBrandIdByName("SomeBrand")).thenReturn(7);

        assertEquals(7, service.resolveAttributedBrandId(snapshot("{}", "{}"), legacy));
        verify(brandMapper, never()).insertSelective(any());
    }

    @Test
    public void usableIdentityFieldPredicateRules() {
        assertFalse(AttributionProvider.usableIdentityField(null));
        assertFalse(AttributionProvider.usableIdentityField(""));
        assertFalse(AttributionProvider.usableIdentityField("  "));
        assertFalse(AttributionProvider.usableIdentityField("{}"));
        assertFalse(AttributionProvider.usableIdentityField("[]"));
        assertFalse(AttributionProvider.usableIdentityField("null"));
        assertFalse(AttributionProvider.usableIdentityField("Undefined"));
        assertFalse(AttributionProvider.usableIdentityField("--/--"));
        assertTrue(AttributionProvider.usableIdentityField("SUP-9"));
        assertTrue(AttributionProvider.usableIdentityField("XIN BO EDUCATIONAL CONSULTATION PTE. LTD."));
        assertTrue(AttributionProvider.usableIdentityField("株式会社")); // non-Latin letters are letters
    }
}
