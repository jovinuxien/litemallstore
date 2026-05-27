package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Goods-management tunables, overridable via litemall-config per environment.
 * Currently carries the stock-low threshold used by the goods-availability
 * check before an order is placed.
 */
@ConfigurationProperties(prefix = "litemall.goods")
public class LitemallGoodsProperties {

    /** Minimum stock count below which a goods/product is considered unavailable. */
    private short stockLowThreshold = 5;

    public short getStockLowThreshold() {
        return stockLowThreshold;
    }

    public void setStockLowThreshold(short stockLowThreshold) {
        this.stockLowThreshold = stockLowThreshold;
    }
}
