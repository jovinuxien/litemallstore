package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Turns a store-currency amount into the figure the CJ createOrderV2 request declares
 * ({@code shopAmount}, {@code products[].unitPrice}) — the one place the declared-amount
 * knobs are read (plan-cj-declared-amount-2026-11-09.md D2/D4):
 *
 * <ul>
 *   <li>{@code litemall.order.cj.declare-store-amount} (default true) — the kill switch;
 *       false ⇒ {@link #toCjAmount} is always null, so every price field is omitted and the
 *       request is byte-identical to the pre-change one.</li>
 *   <li>{@code litemall.order.cj.declared-amount-currency} = {@code usd} (default) |
 *       {@code store}. Every amount CJ returns is documented as USD and the docs are silent
 *       on {@code shopAmount}; {@code usd} divides the store (EUR) amount by the existing
 *       Wave-24 rate {@code litemall.order.fx-usd-eur} (the inverse of the freight seam, so
 *       ONE env var governs both directions); {@code store} sends the amount as-is. Whichever
 *       way CJ support answers, the change is a container recreate, not a rebuild.</li>
 * </ul>
 *
 * 2dp HALF_UP always; a non-positive fx rate falls back to identity with a WARN (the
 * CjFreightQuoteService guard); an unknown currency mode falls back to {@code usd}.
 */
@Component
public class CjDeclaredAmountConverter {

    private static final Logger log = LoggerFactory.getLogger(CjDeclaredAmountConverter.class);

    public static final String MODE_USD = "usd";
    public static final String MODE_STORE = "store";

    private final boolean enabled;
    private final String mode;
    private final BigDecimal fxUsdEur;
    private final String storeCurrency;

    public CjDeclaredAmountConverter(
            @Value("${litemall.order.cj.declare-store-amount:true}") boolean enabled,
            @Value("${litemall.order.cj.declared-amount-currency:usd}") String mode,
            @Value("${litemall.order.fx-usd-eur:1.0}") BigDecimal fxUsdEur,
            @Value("${litemall.order.stripe.currency:usd}") String storeCurrency) {
        this.enabled = enabled;
        String m = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (!MODE_USD.equals(m) && !MODE_STORE.equals(m)) {
            log.warn("litemall.order.cj.declared-amount-currency='{}' is not usd|store — using usd", mode);
            m = MODE_USD;
        }
        this.mode = m;
        if (fxUsdEur == null || fxUsdEur.signum() <= 0) {
            log.warn("litemall.order.fx-usd-eur={} is not a positive rate — declared amounts use identity 1.0", fxUsdEur);
            this.fxUsdEur = BigDecimal.ONE;
        } else {
            this.fxUsdEur = fxUsdEur;
        }
        this.storeCurrency = storeCurrency == null || storeCurrency.isBlank()
                ? "store" : storeCurrency.trim().toUpperCase(Locale.ROOT);
        if (enabled) {
            log.info("CJ declared store amount: ENABLED (declared in {}, store {}, fx-usd-eur {})",
                    declaredCurrencyLabel(), this.storeCurrency, this.fxUsdEur);
        } else {
            log.info("CJ declared store amount: DISABLED — createOrderV2 carries no shopAmount/unitPrice (CJ declares its own amount)");
        }
    }

    /** Kill switch state (D4). */
    public boolean isEnabled() {
        return enabled;
    }

    /** {@code usd} or {@code store}. */
    public String getMode() {
        return mode;
    }

    /** The rate in force (identity when the configured one was unusable). */
    public BigDecimal getFxUsdEur() {
        return fxUsdEur;
    }

    /** ISO label of the amount CJ receives — for the audit INFO line. */
    public String declaredCurrencyLabel() {
        return MODE_USD.equals(mode) ? "USD" : storeCurrency;
    }

    /** ISO label of the store amount — for the audit INFO line. */
    public String storeCurrencyLabel() {
        return storeCurrency;
    }

    /**
     * Store-currency amount → declared amount, 2dp HALF_UP. Null in, null out; disabled ⇒
     * null (field omitted). {@code usd}: {@code amount / fxUsdEur} (EUR→USD is the inverse
     * of the freight seam's USD→EUR multiply); {@code store}: the amount at 2dp.
     */
    public BigDecimal toCjAmount(BigDecimal storeAmount) {
        if (!enabled || storeAmount == null) {
            return null;
        }
        if (MODE_STORE.equals(mode)) {
            return storeAmount.setScale(2, RoundingMode.HALF_UP);
        }
        return storeAmount.divide(fxUsdEur, 2, RoundingMode.HALF_UP);
    }
}
