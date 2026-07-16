package org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment;

/**
 * The result of asking the PSP to reverse a charge.
 *
 * <p>An outcome rather than an exception because the caller must be able to leave the
 * order in REFUND_REQUEST and retryable: a refund that silently didn't happen is worse
 * than one that visibly failed. Callers MUST branch on {@link #isOk()} — treating a
 * failure as success is exactly the bug this type exists to prevent.
 */
public final class RefundOutcome {

    private final boolean ok;
    private final String refundId;
    private final String failureReason;

    private RefundOutcome(boolean ok, String refundId, String failureReason) {
        this.ok = ok;
        this.refundId = refundId;
        this.failureReason = failureReason;
    }

    public static RefundOutcome succeeded(String refundId) {
        return new RefundOutcome(true, refundId, null);
    }

    public static RefundOutcome failed(String failureReason) {
        return new RefundOutcome(false, null, failureReason);
    }

    public boolean isOk() {
        return ok;
    }

    /** The PSP's refund id (re_...), for reconciliation. Null on failure. */
    public String getRefundId() {
        return refundId;
    }

    /** Null on success. */
    public String getFailureReason() {
        return failureReason;
    }
}
