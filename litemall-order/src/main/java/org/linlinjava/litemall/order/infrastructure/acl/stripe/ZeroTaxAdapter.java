package org.linlinjava.litemall.order.infrastructure.acl.stripe;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.TaxCalculationPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.tax.TaxableOrder;

/**
 * The default when {@code litemall.order.tax.enabled} is false — every dev boot, and any
 * deployment not yet registered to collect tax.
 *
 * <p>Returns zero, so {@code tax_price} is 0.00 and checkout behaves exactly as it did
 * before Wave 7. This is the ONLY legitimate source of a zero tax: a provider failure must
 * never land here (see {@code LitemallTaxUnavailableException}) — that distinction is the
 * whole point of the seam. "We are not collecting tax" and "we could not work out the tax"
 * must never look the same.
 */
public class ZeroTaxAdapter implements TaxCalculationPort {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public TaxQuote quote(TaxableOrder order) {
        return TaxQuote.zero();
    }
}
