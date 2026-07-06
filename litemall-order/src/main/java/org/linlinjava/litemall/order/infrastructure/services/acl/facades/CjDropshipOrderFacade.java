package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;

import java.util.List;

/**
 * Anti-corruption seam over CJ Dropshipping order placement. The (future) checkout-routing and any
 * REST surface depend on THIS interface only — never the Feign client — mirroring
 * {@code LitemallGoodsFacade}. A CJ failure surfaces as {@code LitemallCjOrderException}.
 */
public interface CjDropshipOrderFacade {

    /**
     * Place a CJ Dropshipping order for the given lines + shipping address.
     *
     * @return the CJ order id/number/status on success
     * @throws org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException
     *         if CJ is unreachable, the circuit is open, or CJ returns a business error
     */
    CjOrderResult placeOrder(CjOrderPlacement placement);

    /**
     * Best-effort logistics quote for a destination country + CJ variant lines (checkout preview):
     * the same freightCalculate selection {@link #placeOrder} uses, but exposed pre-payment.
     *
     * @return the chosen option, or {@code null} when CJ offers nothing / is unreachable — never throws
     */
    CjLogisticsOption quoteLogistics(String endCountryCode, List<CjOrderPlacement.Line> lines);
}
