package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
 * {@code cost} is persisted ({@code litemall_cj_product.sell_price},
 * {@code litemall_goods[_product].cost}), and the margin default is
 * {@code spring.cjdropship.pricing.margin} = 1.25.
 *
 * <p>Wave 24: CJ quotes USD but the store charges EUR — {@code litemall.goods.fx-usd-eur}
 * (default 1.0 identity) converts raw CJ amounts at intake ({@link #parseCost}/{@link #cost}),
 * so stored cost and every retail derived from it persist in the store currency and all
 * ratio-based math downstream needs no currency awareness.
 *
 * <p>CJ {@code sellPrice} strings may be a single value ("11.85") or a variant range
 * ("14.71 -- 64.38") — a range collapses to its LOWER bound (the entry-level "from" cost).
 */
@Component
public class CjPricing {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjPricing.class);

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final CJDropshippingConfig config;
    private final CategoryMarginResolver marginResolver;
    private final BigDecimal fxUsdEur;

    /** Global-margin-only pricing (tests and non-category contexts). */
    public CjPricing(CJDropshippingConfig config) {
        this(config, (CategoryMarginResolver) null);
    }

    public CjPricing(CJDropshippingConfig config, CategoryMarginResolver marginResolver) {
        this(config, marginResolver, BigDecimal.ONE);
    }

    /** Explicit-fx pricing (tests; production wires litemall.goods.fx-usd-eur). */
    public CjPricing(CJDropshippingConfig config, CategoryMarginResolver marginResolver, BigDecimal fxUsdEur) {
        this.config = config;
        this.marginResolver = marginResolver;
        this.fxUsdEur = fxUsdEur != null ? fxUsdEur : BigDecimal.ONE;
    }

    @Autowired
    public CjPricing(CJDropshippingConfig config, ObjectProvider<CategoryMarginResolver> marginResolver,
                     LitemallGoodsProperties goodsProperties) {
        this(config, marginResolver.getIfAvailable(), goodsProperties.getFxUsdEur());
    }

    /**
     * Store-currency cost from a CJ {@code sellPrice} string (range → lower bound); null when
     * unparseable. CJ quotes USD; Wave 24 multiplies by {@code litemall.goods.fx-usd-eur}
     * (default 1.0 identity) before the single 2dp HALF_UP rounding.
     */
    public BigDecimal parseCost(String sellPrice) {
        if (sellPrice == null || sellPrice.isBlank()) {
            return null;
        }
        Matcher m = FIRST_NUMBER.matcher(sellPrice);
        if (!m.find()) {
            LOGGER.warn("Unparseable CJ sellPrice '{}'", sellPrice);
            return null;
        }
        return toStore(new BigDecimal(m.group()));
    }

    /** Store-currency cost from a numeric CJ USD price (fx applied); null-safe. */
    public BigDecimal cost(Double sellPrice) {
        return sellPrice == null ? null : toStore(BigDecimal.valueOf(sellPrice));
    }

    /** Raw CJ USD → store currency: × fx, then 2dp HALF_UP (one rounding, after the multiply). */
    private BigDecimal toStore(BigDecimal usd) {
        return usd.multiply(fxUsdEur).setScale(2, RoundingMode.HALF_UP);
    }

    /** Retail = cost × global margin (default 1.25), 2dp HALF_UP; null when the cost is unknown. */
    public BigDecimal retail(BigDecimal cost) {
        return retail(cost, null);
    }

    /** Retail = cost × the given margin (null margin → global), 2dp HALF_UP; null on no cost. */
    public BigDecimal retail(BigDecimal cost, BigDecimal margin) {
        if (cost == null) {
            return null;
        }
        BigDecimal m = margin != null ? margin : config.getPricing().getMargin();
        return cost.multiply(m).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Retail priced with the effective margin of the raw CJ leaf category (Wave 14: per-L1
     * override, else global). Sync/enrichment reprice site — falls back to the global margin
     * when no resolver is wired (tests) or the leaf is unknown.
     */
    public BigDecimal retailForCjLeaf(BigDecimal cost, String cjLeafUuid) {
        return retail(cost, marginResolver != null ? marginResolver.effectiveForCjLeaf(cjLeafUuid) : null);
    }

    /** Retail priced with the effective margin of a local category id (promote/insight paths). */
    public BigDecimal retailForCategory(BigDecimal cost, Integer categoryId) {
        return retail(cost, marginResolver != null ? marginResolver.effectiveForCategory(categoryId) : null);
    }

    /** Effective margin for a local category id; global when no resolver is wired. */
    public BigDecimal marginForCategory(Integer categoryId) {
        return marginResolver != null
                ? marginResolver.effectiveForCategory(categoryId)
                : config.getPricing().getMargin();
    }

    /** Effective margin for a raw CJ leaf category UUID; global when no resolver is wired. */
    public BigDecimal marginForCjLeaf(String cjLeafUuid) {
        return marginResolver != null
                ? marginResolver.effectiveForCjLeaf(cjLeafUuid)
                : config.getPricing().getMargin();
    }
}
