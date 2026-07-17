package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentDraft;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentVerification;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentWebhookEvent;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;

/**
 * The PSP seam (Wave 7, Task A — docs/adr-stripe-payments.md). Selected by
 * {@code litemall.order.stripe.enabled} in {@link
 * org.linlinjava.litemall.order.infrastructure.configuration.FulfillmentSeamsConfiguration}.
 *
 * <p><b>This port does not follow the never-throws contract</b> of {@code ExpressQueryPort}
 * / {@code ReceiptPrinterPort}. Those collapse errors into an empty answer because a
 * missing tracking number or an unprinted receipt is survivable. Money is not: an error
 * that reads as "no result" would become "not paid" or "refunded but nothing moved". So
 * verification returns an explicit accept/reject with a reason, and the operations that
 * can only be reported honestly (create-intent, webhook parsing) throw
 * {@link org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException}.
 *
 * <p>Everything here FAILS CLOSED. The disabled adapter rejects rather than accepts, so
 * a mis-set flag cannot produce a paid order.
 */
public interface PaymentGatewayPort {

    /** Whether a real PSP is wired. Gates the SPA's card affordance; never gates verification. */
    boolean enabled();

    /**
     * Create a PaymentIntent for an order, server-side, so the amount cannot originate in
     * the browser. Carries {@code metadata.orderId} — {@link #verify} asserts it back.
     *
     * @throws org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException
     *         disabled, or the PSP refused/was unreachable. Never returns a stub.
     */
    PaymentIntentDraft createIntent(Integer orderId, LitemallMoney amount);

    /**
     * Verify a client-confirmed PaymentIntent against the order it claims to pay: it must
     * exist, be {@code succeeded}, have {@code amount_received} equal to the expected
     * amount, match the configured currency, and carry this order's id in metadata.
     *
     * <p>Never throws: an unreachable PSP is a rejection like any other, because the only
     * safe answer to "did this get paid?" when we cannot tell is "no".
     */
    PaymentVerification verify(String paymentIntentId, Integer orderId, LitemallMoney expectedAmount);

    /**
     * Reverse a captured charge. Returns an outcome rather than throwing so the caller can
     * keep the order retryable — a refund that silently didn't happen is worse than one
     * that visibly failed.
     */
    RefundOutcome refund(String paymentIntentId, LitemallMoney amount, Integer orderId);

    /**
     * Verify the {@code Stripe-Signature} header and parse the payload.
     *
     * @throws org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException
     *         signature invalid/absent, secret unset, or the body is unparseable. The
     *         webhook is anonymous, so this signature IS its authentication.
     */
    PaymentWebhookEvent parseWebhook(String payload, String signatureHeader);
}
