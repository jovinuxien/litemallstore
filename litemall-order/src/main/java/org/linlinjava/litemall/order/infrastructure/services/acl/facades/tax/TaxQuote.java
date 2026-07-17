package org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax;

import java.math.BigDecimal;

/**
 * Tax owed on an order, plus the provider's per-jurisdiction detail.
 *
 * <p>{@code breakdown} is stored on the order for invoices and audit and is never summed
 * back into a total — the single authoritative number is {@link #getAmount()}.
 */
public final class TaxQuote {

    private static final TaxQuote ZERO = new TaxQuote(BigDecimal.ZERO.setScale(2), null, null);

    private final BigDecimal amount;
    private final String breakdownJson;
    private final String providerReference;

    public TaxQuote(BigDecimal amount, String breakdownJson, String providerReference) {
        this.amount = amount;
        this.breakdownJson = breakdownJson;
        this.providerReference = providerReference;
    }

    /** No tax collected — tax disabled. NOT a fallback for a provider failure. */
    public static TaxQuote zero() {
        return ZERO;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** Provider JSON, or null when there is nothing to record. */
    public String getBreakdownJson() {
        return breakdownJson;
    }

    /** The provider's calculation id, for reconciling a filing back to an order. */
    public String getProviderReference() {
        return providerReference;
    }
}
