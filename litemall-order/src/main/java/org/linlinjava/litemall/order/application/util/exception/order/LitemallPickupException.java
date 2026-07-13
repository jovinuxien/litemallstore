package org.linlinjava.litemall.order.application.util.exception.order;

/**
 * A pickup submit violated a pickup precondition (Wave 4, Task B): pickup disabled,
 * CJ lines in the cart, store missing/hidden, or blank pickup contact. Normally raised
 * in the orchestrator PRE-CHECK zone (clean 422 before any transaction work); when the
 * transactional placeOrder trips its safety-net copy of a check (e.g. the store was
 * hidden mid-flight), this exception is RETHROWN through the orchestrator — never
 * converted inside the transaction (the rollback-only→502 landmine) — and the REST
 * layer maps it to a 422 submit-failed envelope.
 */
public class LitemallPickupException extends RuntimeException {

    public LitemallPickupException(String message) {
        super(message);
    }
}
