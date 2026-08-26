package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression guard for a defect that shipped and was caught in production the same day.
 *
 * <p>Supplier prefix stripping first lived only in {@code CatalogHygieneService}, which rewrites
 * {@code litemall_goods} in place. That cleaned 714 rows — and then a full promote run, executed
 * an hour later for an unrelated price change, wrote all 68 live prefixes straight back, because
 * THIS adapter derives {@code goods.name} from the CJ snapshot title on every promote. The
 * nightly promote would have done the same thing every night.
 *
 * <p>These tests pin the rule at the seam where the name is actually computed. If someone moves
 * it back into a cleanup pass, they fail.
 */
public class CjProductToNativeAdapterTitleTest {

    private CjProductToNativeAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new CjProductToNativeAdapter(new ObjectMapper());
    }

    private static LitemallCjProduct row(String title) {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid("pid-1");
        row.setTitle(title);
        row.setPrice(new BigDecimal("10.00"));
        row.setSellPrice(new BigDecimal("8.00"));
        return row;
    }

    @Test
    public void supplierPrefixIsStrippedAtPromoteNotJustByHygiene() {
        // Verbatim from the live catalogue, fullwidth colon (U+FF1A) and all.
        NativeGoodsAggregate agg = adapter.adapt(
                row("Support Pan European：2 IN 1 Battery Leaf Blower 18V With 2 Batteries"));

        assertEquals("2 IN 1 Battery Leaf Blower 18V With 2 Batteries",
                agg.getGoods().getName());
    }

    @Test
    public void keywordsKeepTheRawSupplierTitleSoSearchRecallIsUnchanged() {
        String raw = "Support Pan European：Crane Scale Up To 300kg Digital Hanging Scale";
        NativeGoodsAggregate agg = adapter.adapt(row(raw));

        assertEquals(raw, agg.getGoods().getKeywords(),
                "keywords is the LIKE-matched search column — a shopper searching the untrimmed "
                        + "string must still find the product");
    }

    @Test
    public void asciiColonAndOddSpacingAreHandled() {
        assertEquals("Water Pressure Reducer",
                adapter.adapt(row("Support Pan European: Water Pressure Reducer"))
                        .getGoods().getName());
        assertEquals("Water Pressure Reducer",
                adapter.adapt(row("  support  pan  european：Water Pressure Reducer"))
                        .getGoods().getName());
    }

    @Test
    public void anOrdinaryTitleIsUntouched() {
        String clean = "Solar-powered Outdoor Courtyard Light Waterproof Lawn Lamp";
        assertEquals(clean, adapter.adapt(row(clean)).getGoods().getName());
    }

    @Test
    public void aTitleThatIsOnlyThePrefixIsNotEmptied() {
        // Never trade a bad name for a blank one; hygiene's readability check then routes it
        // to rename-or-retire.
        assertEquals("Support Pan European：",
                adapter.adapt(row("Support Pan European：")).getGoods().getName());
    }

    @Test
    public void aNullTitleStaysNull() {
        assertNull(adapter.adapt(row(null)).getGoods().getName());
    }
}
