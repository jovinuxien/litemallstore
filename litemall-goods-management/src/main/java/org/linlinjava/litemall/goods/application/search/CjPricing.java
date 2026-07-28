package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single home of CJ pricing math (Wave 12): {@code retail = cjCost × margin}.
 *
 * <p>Before Wave 12 the formula ({@code sellPrice × usdToCny 7.2 × margin 2.0}) was duplicated
 * across the sync, enrichment and detail services — and the raw wholesale cost was discarded.
 * The currency factor is GONE (deliberately deleted, not set to 1): prices now live in the USD
 * basis, {@code cost} is persisted ({@code litemall_cj_product.sell_price},
 * {@code litemall_goods[_product].cost}), and the margin default is
 * {@code spring.cjdropship.pricing.margin} = 1.25.
 *
 * <p>CJ {@code sellPrice} strings may be a single value ("11.85") or a variant range
 * ("14.71 -- 64.38") — a range collapses to its LOWER bound (the entry-level "from" cost).
 */
@Component
public class CjPricing {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjPricing.class);

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final CJDropshippingConfig config;

    public CjPricing(CJDropshippingConfig config) {
        this.config = config;
    }

    /** Raw USD cost from a CJ {@code sellPrice} string (range → lower bound); null when unparseable. */
    public BigDecimal parseCost(String sellPrice) {
        if (sellPrice == null || sellPrice.isBlank()) {
            return null;
        }
        Matcher m = FIRST_NUMBER.matcher(sellPrice);
        if (!m.find()) {
            LOGGER.warn("Unparseable CJ sellPrice '{}'", sellPrice);
            return null;
        }
        return new BigDecimal(m.group()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Raw USD cost from a numeric CJ price; null-safe. */
    public BigDecimal cost(Double sellPrice) {
        return sellPrice == null ? null : BigDecimal.valueOf(sellPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /** Retail = cost × margin (default 1.25), 2dp HALF_UP; null when the cost is unknown. */
    public BigDecimal retail(BigDecimal cost) {
        if (cost == null) {
            return null;
        }
        return cost.multiply(config.getPricing().getMargin()).setScale(2, RoundingMode.HALF_UP);
    }
}
