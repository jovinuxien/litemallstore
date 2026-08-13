package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wave 25.1: the SKU photo follows the captured per-variant image ({@code variant_image} in
 * {@code variants_json}) so the already-live SPA half can switch the PDP photo per variant.
 * The fallback is ALWAYS the main photo — an unusable image (foreign host, oversized) never
 * truncates into a broken URL, and an absent main photo stays null (never ""), so the selective
 * update can never blank a previously landed url.
 */
public class CjProductToNativeAdapterVariantImageTest {

    private static final String MAIN = "https://cf.cjdropshipping.com/main/photo.jpg";
    private static final String VARIANT = "https://cf.cjdropshipping.com/variant/red.jpg";
    private static final String VARIANT_OSS = "https://oss-cf.cjdropshipping.com/variant/blue.jpg";

    private CjProductToNativeAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new CjProductToNativeAdapter(new ObjectMapper());
    }

    private static LitemallCjProduct row(String imageUrl, String variantsJson) {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-1");
        row.setTitle("T");
        row.setImageUrl(imageUrl);
        row.setVariantsJson(variantsJson);
        return row;
    }

    @Test
    public void variantImageLandsOnTheSkuUrlAndImagelessVariantsKeepTheMainPhoto() {
        NativeGoodsAggregate agg = adapter.adapt(row(MAIN,
                "[{\"vid\":\"v1\",\"variant_image\":\"" + VARIANT + "\",\"stock\":5},"
                        + "{\"vid\":\"v2\",\"variant_image\":\"" + VARIANT_OSS + "\",\"stock\":5},"
                        + "{\"vid\":\"v3\",\"stock\":5}]"));

        assertEquals(VARIANT, agg.getProducts().get(0).getUrl());
        assertEquals(VARIANT_OSS, agg.getProducts().get(1).getUrl());
        assertEquals(MAIN, agg.getProducts().get(2).getUrl(), "no variant image -> main photo");
    }

    @Test
    public void uncoveredHostFallsBackToTheMainPhoto() {
        // Not behind the /_cdn edge rewrite -> would serve unproxied/mixed content on the PDP.
        NativeGoodsAggregate agg = adapter.adapt(row(MAIN,
                "[{\"vid\":\"v1\",\"variant_image\":\"https://cc-west-usa.oss-us-west-1.aliyuncs.com/x.jpg\",\"stock\":5}]"));

        assertEquals(MAIN, agg.getProducts().get(0).getUrl());
    }

    @Test
    public void oversizedVariantImageFallsBackInsteadOfTruncating() {
        String tooLong = "https://cf.cjdropshipping.com/" + "a".repeat(120) + ".jpg"; // > 125 chars
        NativeGoodsAggregate agg = adapter.adapt(row(MAIN,
                "[{\"vid\":\"v1\",\"variant_image\":\"" + tooLong + "\",\"stock\":5}]"));

        assertEquals(MAIN, agg.getProducts().get(0).getUrl(),
                "a truncated URL is a broken image, not a shorter one");
    }

    @Test
    public void absentMainPhotoAndNoVariantImageLeavesUrlNullNeverBlank() {
        // null (not "") -> the mapper's selective update skips the column, so an existing
        // litemall_goods_product.url is never blanked by a later promote.
        NativeGoodsAggregate agg = adapter.adapt(row(null, "[{\"vid\":\"v1\",\"stock\":5}]"));
        assertNull(agg.getProducts().get(0).getUrl());

        // synthetic single-SKU path (no variants at all) obeys the same rule
        NativeGoodsAggregate synthetic = adapter.adapt(row(null, null));
        assertNull(synthetic.getProducts().get(0).getUrl());
    }

    @Test
    public void usableVariantImageValidatorRules() {
        assertNull(CjProductToNativeAdapter.usableVariantImage(null));
        assertNull(CjProductToNativeAdapter.usableVariantImage("  "));
        assertNull(CjProductToNativeAdapter.usableVariantImage("https://evil.example.com/x.jpg"));
        assertNull(CjProductToNativeAdapter.usableVariantImage("cf.cjdropshipping.com/x.jpg"), "scheme required");
        assertEquals(VARIANT, CjProductToNativeAdapter.usableVariantImage(" " + VARIANT + " "), "trimmed");
        assertEquals("http://oss-cf.cjdropshipping.com/y.jpg",
                CjProductToNativeAdapter.usableVariantImage("http://oss-cf.cjdropshipping.com/y.jpg"),
                "http variant is covered by the edge rewrite too");
    }

    @Test
    public void variantImageNeverLeaksIntoPriceOrCostSemantics() {
        // Regression guard: the Wave-12 charge-integrity behavior is untouched by the new field.
        LitemallCjProduct r = row(MAIN,
                "[{\"vid\":\"v1\",\"variant_image\":\"" + VARIANT + "\",\"variant_sell_price\":8.00,"
                        + "\"variant_price\":10.00,\"stock\":50}]");
        r.setSellPrice(new java.math.BigDecimal("8.00"));
        r.setPrice(new java.math.BigDecimal("10.00"));
        LitemallGoodsProduct sku = adapter.adapt(r).getProducts().get(0);
        assertEquals(0, sku.getPrice().compareTo(new java.math.BigDecimal("10.00")));
        assertEquals(0, sku.getCost().compareTo(new java.math.BigDecimal("8.00")));
        assertEquals(VARIANT, sku.getUrl());
    }
}
