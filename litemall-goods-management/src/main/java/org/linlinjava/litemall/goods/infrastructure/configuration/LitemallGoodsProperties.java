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
