package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Goods-management tunables, overridable via litemall-config per environment.
 * Currently carries the stock-low threshold used by the goods-availability
 * check before an order is placed.
 */
@ConfigurationProperties(prefix = "litemall.goods")
public class LitemallGoodsProperties {

    /**
     * Wave 24: USD→EUR conversion applied to raw CJ amounts at cost landing (CjPricing
     * intake). 1.0 = identity (dev-safe default); prod sets the real rate at deploy via
     * LITEMALL_FX_USD_EUR. Stored cost/retail then persist in the store currency, so all
     * ratio-based math downstream (margin guard, insight, deal floors) is unaffected.
     */
    private BigDecimal fxUsdEur = BigDecimal.ONE;

    /** Minimum stock count below which a goods/product is considered unavailable. */
    private short stockLowThreshold = 5;

    /**
     * Wave 26 Phase 2: minimum retail price a good may carry and stay on sale. 0 = OFF (default,
     * dev-safe and byte-identical to pre-Wave-26 behaviour); prod sets LITEMALL_GOODS_PRICE_FLOOR.
     *
     * <p>A sub-floor good is taken OFF SALE, never silently repriced: retail is cost × the
     * category's effective margin, and quietly bending that would break the invariant the margin
     * guard, deal floors and coupon guard all compute against. Off-sale is reversible (the row and
     * its data survive; raising the margin or lowering the floor brings it back on the next cycle).
     *
     * <p>Context: 1,056 live SKUs priced under EUR 1 as of 2026-08-12 — at EUR 6.93 flat freight
     * those are loss-makers and a Merchant Center review risk.
     */
    private BigDecimal priceFloor = BigDecimal.ZERO;

    public BigDecimal getPriceFloor() {
        return priceFloor;
    }

    public void setPriceFloor(BigDecimal priceFloor) {
        this.priceFloor = priceFloor;
    }

    /** True when a floor is configured AND this retail falls below it. Null retail never trips it. */
    public boolean isBelowPriceFloor(BigDecimal retail) {
        return priceFloor != null && priceFloor.signum() > 0
                && retail != null && retail.compareTo(priceFloor) < 0;
    }

    /**
     * Maximum retail price a good may carry and stay on sale. 0 = OFF (default, dev-safe and
     * byte-identical to pre-ceiling behaviour); prod sets LITEMALL_GOODS_PRICE_CEILING.
     *
     * <p>The mirror image of {@link #priceFloor}, and off-sale for the same reason: retail is
     * cost × the category's effective margin, so a good priced out of the band is a data problem
     * (an outlier CJ cost, a per-unit price quoted for a pallet), not something to silently
     * rewrite. Bending the price here would break the invariant the margin guard, deal floors
     * and coupon guard all compute against. Reversible: clear the ceiling and the next cycle
     * puts the row back on sale.
     *
     * <p>Context: the live feed on 2026-08-26 carried 97 items above EUR 500, topping out at a
     * EUR 32,819 sideboard and a EUR 12,011 garage. Nobody dropships a EUR 32k sideboard; on a
     * young merchant account those rows read as a pricing fault and invite a misrepresentation
     * review, which suspends the whole account rather than the offending item.
     */
    private BigDecimal priceCeiling = BigDecimal.ZERO;

    public BigDecimal getPriceCeiling() {
        return priceCeiling;
    }

    public void setPriceCeiling(BigDecimal priceCeiling) {
        this.priceCeiling = priceCeiling;
    }

    /** True when a ceiling is configured AND this retail exceeds it. Null retail never trips it. */
    public boolean isAbovePriceCeiling(BigDecimal retail) {
        return priceCeiling != null && priceCeiling.signum() > 0
                && retail != null && retail.compareTo(priceCeiling) > 0;
    }

    /**
     * Wave 26 Phase 2: L1 root category ids the storefront is narrowed to. EMPTY = OFF (default,
     * byte-identical to pre-Wave-26 behaviour); prod sets LITEMALL_GOODS_ANCHOR_CATEGORY_IDS.
     *
     * <p>Why this exists: narrowing off-sales the goods that exist TODAY, but the CJ pipeline
     * deliberately keeps mirroring all 14 L1s and the nightly promote lands every NEW product on
     * sale. Measured on prod one night after narrowing: 428 new goods went on sale, 328 of them
     * outside the anchor — the storefront drifts back to broad within weeks and the narrowing
     * silently undoes itself.
     *
     * <p>Applies to NEW goods only, deliberately. A standing rule (like the price floor) would
     * re-off-sale whatever {@code /insight/narrow/restore} had just put back, turning the
     * reversibility guarantee into a lie; on existing goods, on-sale stays an admin decision.
     */
    private List<Integer> anchorCategoryIds = new ArrayList<>();

    public List<Integer> getAnchorCategoryIds() {
        return anchorCategoryIds;
    }

    public void setAnchorCategoryIds(List<Integer> anchorCategoryIds) {
        this.anchorCategoryIds = anchorCategoryIds == null ? new ArrayList<>() : anchorCategoryIds;
    }

    /**
     * True when narrowing is configured AND this L1 root sits outside it — i.e. a NEW good here
     * must not land on sale. An unresolvable root counts as OUTSIDE: "we cannot place it" must
     * not silently mean "put it in the storefront", the same call the narrowing sweep makes.
     */
    public boolean isOutsideAnchor(Integer l1RootId) {
        if (anchorCategoryIds == null || anchorCategoryIds.isEmpty()) {
            return false;
        }
        return l1RootId == null || !anchorCategoryIds.contains(l1RootId);
    }

    /**
     * Display/JSON-LD currency served by /srv/goods/meta (Wave 13) — uppercase ISO for
     * schema.org offers. Mirrors what the storefront actually charges: the order service's
     * LITEMALL_ORDER_STRIPE_CURRENCY (default "usd", prod unoverridden).
     */
    private String currency = "USD";

    public short getStockLowThreshold() {
        return stockLowThreshold;
    }

    public void setStockLowThreshold(short stockLowThreshold) {
        this.stockLowThreshold = stockLowThreshold;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getFxUsdEur() {
        return fxUsdEur;
    }

    public void setFxUsdEur(BigDecimal fxUsdEur) {
        this.fxUsdEur = fxUsdEur;
    }
}
