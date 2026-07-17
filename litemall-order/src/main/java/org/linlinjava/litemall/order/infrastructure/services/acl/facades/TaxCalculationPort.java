package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxableOrder;

/**
 * Tax calculation seam (Wave 7, Task C — docs/adr-stripe-payments.md): US sales tax and
 * EU VAT. Selected by {@code litemall.order.tax.provider} in {@link
 * org.linlinjava.litemall.order.infrastructure.configuration.FulfillmentSeamsConfiguration}.
 *
 * <p><b>This port FAILS CLOSED — the one deliberate exception to this module's fail-soft
 * habit.</b> {@code ExpressQueryPort} returns empty on error and {@code ReceiptPrinterPort}
 * never throws, because a missing tracking number or an unprinted receipt is recoverable.
 * An untaxed sale is not: the money is gone, the liability stays, and nobody notices until
 * an audit. So when tax is enabled and cannot be computed, {@link #quote} THROWS and
 * checkout is blocked.
 *
 * <p>The same port serves the checkout preview and the submit path, which is what makes
 * the quoted total and the charged total provably the same number.
 */
public interface TaxCalculationPort {

    /** Whether tax is actually being collected. False ⇒ {@link #quote} returns zero. */
    boolean enabled();

    /**
     * Compute tax for an order about to be placed (or previewed).
     *
     * @throws org.linlinjava.litemall.order.application.util.exception.tax.LitemallTaxUnavailableException
     *         enabled but uncomputable — provider unreachable, refused, or the destination
     *         is unusable. Callers must NOT catch this into a zero: that is the entire
     *         failure mode this port exists to prevent.
     */
    TaxQuote quote(TaxableOrder order);
}
