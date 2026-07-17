package org.linlinjava.litemall.order.application.util.exception.product;

/**
 * A checked cart line's price no longer matches goods-management's current price.
 *
 * <p>Thrown by the authoritative re-stamp at submit (Wave 7, Task E0). The order total
 * is only ever built from catalog prices, so the server COULD silently recompute — but
 * that charges an amount the customer never saw. Fail with a clean 422 and let them
 * review the cart instead.
 *
 * <p>Legitimately trips when a price moves while a cart sits idle, including a flash
 * deal opening or closing under it (the deal lifecycle swaps the SKU rows on a ~60s
 * tick, so catalog price and the cart's snapshot can disagree for a window).
 *
 * <p>Like the rest of this family, it is RETHROWN through the orchestrator and caught in
 * the REST controller — never converted inside the transaction (the rollback-only→502
 * landmine).
 */
public class LitemallPriceChangedException extends RuntimeException {

    public LitemallPriceChangedException(String message) {
        super(message);
    }
}
