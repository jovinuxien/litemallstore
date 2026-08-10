package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wave-12 charge-integrity guard: checkout money reads litemall_goods_product.price, and the
 * upsert deliberately never refreshes enrichment-owned variants_json — so after the ×1.25
 * repricing an old-basis variant_price (no variant_sell_price alongside) MUST be replaced by
 * the product retail at promote time, or the storefront would display the new price while
 * checkout charged the old ×14.4 one.
 */
public class CjProductToNativeAdapterPricingTest {

    private CjProductToNativeAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new CjProductToNativeAdapter(new ObjectMapper());
    }

    private static LitemallCjProduct row(String sellPrice, String price, String variantsJson) {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-1");
        row.setTitle("T");
        row.setSellPrice(sellPrice == null ? null : new BigDecimal(sellPrice));
        row.setPrice(price == null ? null : new BigDecimal(price));
        row.setVariantsJson(variantsJson);
        return row;
    }

    @Test
    public void staleBasisVariantPriceIsReplacedByRepricedRetail() {
        // Old-era variants_json: variant_price 170.64 (cost × 14.4), NO variant_sell_price.
        NativeGoodsAggregate agg = adapter.adapt(row("11.85", "14.81",
                "[{\"vid\":\"v1\",\"variant_price\":170.64,\"stock\":100}]"));

        LitemallGoodsProduct sku = agg.getProducts().get(0);
        assertEquals(0, sku.getPrice().compareTo(new BigDecimal("14.81")), "stale price must not survive repricing");
        assertEquals(0, sku.getCost().compareTo(new BigDecimal("11.85")), "product-level cost fallback");
    }

    @Test
    public void costedVariantKeepsItsOwnPriceAndCost() {
        NativeGoodsAggregate agg = adapter.adapt(row("11.85", "14.81",
                "[{\"vid\":\"v1\",\"variant_sell_price\":8.00,\"variant_price\":10.00,\"stock\":50}]"));

        LitemallGoodsProduct sku = agg.getProducts().get(0);
        assertEquals(0, sku.getPrice().compareTo(new BigDecimal("10.00")));
        assertEquals(0, sku.getCost().compareTo(new BigDecimal("8.00")));
    }

    @Test
    public void unrepricedRowKeepsLegacyVariantPriceAndNullCost() {
        // No sell_price captured yet (row not re-synced since V45): nothing repriced, keep as-is.
        NativeGoodsAggregate agg = adapter.adapt(row(null, "170.64",
                "[{\"vid\":\"v1\",\"variant_price\":170.64,\"stock\":100}]"));

        LitemallGoodsProduct sku = agg.getProducts().get(0);
        assertEquals(0, sku.getPrice().compareTo(new BigDecimal("170.64")));
        assertNull(sku.getCost());
    }

    @Test
    public void unitNormalizationNeverEmitsCjkGlyphs() {
        // Wave-25 hygiene: no fake default unit, CJK glyph units blank out, real units pass.
        assertEquals("", CjProductToNativeAdapter.normalizeUnit(null));
        assertEquals("", CjProductToNativeAdapter.normalizeUnit("  "));
        assertEquals("", CjProductToNativeAdapter.normalizeUnit("件"));
        assertEquals("", CjProductToNativeAdapter.normalizeUnit("盒装"));
        assertEquals("pc", CjProductToNativeAdapter.normalizeUnit(" pc "));
    }
}
