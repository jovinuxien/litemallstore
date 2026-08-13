package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.math.BigDecimal;

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
