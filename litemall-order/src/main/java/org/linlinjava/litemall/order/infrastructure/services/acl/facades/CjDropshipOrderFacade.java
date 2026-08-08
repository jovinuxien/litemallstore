package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjBalance;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption seam over CJ Dropshipping order placement + lifecycle. The application layer
 * depends on THIS interface only — never the Feign client — mirroring {@code LitemallGoodsFacade}.
 *
 * <p>Two failure contracts coexist deliberately: {@link #placeOrder} throws (it runs inside the
 * pay transaction, where a CJ rejection must roll the payment back), while the lifecycle
 * operations below are BEST-EFFORT (boolean / {@link Optional}) — they run from post-commit
 * hooks and the status-sync poller, which simply retry next sweep on a miss.
 */
public interface CjDropshipOrderFacade {

    /**
     * Place a CJ Dropshipping order for the given lines + shipping address, via
     * {@code createOrderV2} with {@code payType=3} (create-only draft — no CJ money moves
     * inside the caller's transaction; see docs/adr-cj-lifecycle-parity.md).
     *
     * @return the CJ order id/number/status on success
     * @throws org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException
     *         if CJ is unreachable, the circuit is open, or CJ returns a business error
     */
    CjOrderResult placeOrder(CjOrderPlacement placement);

    /**
     * Best-effort logistics quote for a destination country + CJ variant lines (checkout preview):
     * EVERY line freightCalculate offers for the shipment, in CJ's order — the delivery-option
     * chooser renders these. {@link #placeOrder} draws its own selection from the same call.
     *
     * @return the offered options, or an empty list when CJ offers nothing / is unreachable — never throws
     */
    List<CjLogisticsOption> quoteLogisticsOptions(String endCountryCode, List<CjOrderPlacement.Line> lines);

    /**
     * The selection rule shared by checkout preview and placement: {@code preferredName}
     * (the customer's pick) when offered, else the configured default line, else the
     * cheapest offered line. Pure — no CJ call.
     *
     * @return the chosen option, or {@code null} for an empty offer list
     */
    CjLogisticsOption chooseLogistics(List<CjLogisticsOption> options, String preferredName);

    /** Confirm a CREATED CJ order (precondition of payment). Best-effort; never throws. */
    boolean confirmOrder(String cjOrderId);

    /** Pay an UNPAID CJ order from the account balance. Best-effort; never throws. */
    boolean payBalance(String cjOrderId);

    /**
     * CJ-side order detail for the status-sync poll.
     *
     * @return the snapshot, or empty when CJ gave no usable answer — never throws
     */
    Optional<CjOrderSnapshot> fetchOrderDetail(String cjOrderId);

    /**
     * Delete a CJ order (CJ allows it only while still CREATED / IN_CART; a rejection for a
     * further-along order is logged, not raised). Best-effort; never throws.
     */
    boolean deleteOrder(String cjOrderId);

    /** CJ account balance for the admin readout. Empty when CJ gave no usable answer. */
    Optional<CjBalance> getBalance();
}
