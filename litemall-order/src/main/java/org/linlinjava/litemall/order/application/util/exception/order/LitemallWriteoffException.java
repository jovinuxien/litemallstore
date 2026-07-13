package org.linlinjava.litemall.order.application.util.exception.order;

/**
 * A write-off (核销) scan was refused (Wave 4, Task B). Three DISTINCT client errors —
 * the admin controller maps each to a 422 whose message names the exact problem:
 * <ul>
 *   <li>{@link Kind#UNKNOWN_CODE} — no order carries the scanned verify code;</li>
 *   <li>{@link Kind#ALREADY_VERIFIED} — the code was already redeemed (includes losing
 *       a double-scan race on the conditional UPDATE);</li>
 *   <li>{@link Kind#WRONG_STATE} — the order exists but is not a redeemable paid
 *       pickup order (unpaid, refund in progress, not a pickup order, ...).</li>
 * </ul>
 * Thrown out of the @Transactional orchestrator and caught in the controller (outside
 * the transaction proxy), so no rollback-only trap.
 */
public class LitemallWriteoffException extends RuntimeException {

    public enum Kind {
        UNKNOWN_CODE, ALREADY_VERIFIED, WRONG_STATE
    }

    private final Kind kind;

    public LitemallWriteoffException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
