package org.linlinjava.litemall.order.infrastructure.acl.stripe;

import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentDraft;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentVerification;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentWebhookEvent;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default when {@code litemall.order.stripe.enabled} is false — i.e. every dev boot
 * and any environment without keys.
 *
 * <p>It exists to make "no PSP configured" behave like a closed door rather than an open
 * one. It is the whole reason a mis-set flag cannot mint paid orders: {@link #verify}
 * REJECTS. Before Wave 7 this position was occupied by code that accepted any non-blank
 * string, which is how {@code {"paymentIntentId":"x"}} used to buy things.
 *
 * <p>Wallet checkout is unaffected — it never reaches this port.
 */
public class DisabledPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(DisabledPaymentGatewayAdapter.class);

    private static final String DISABLED =
            "Card payment is not available: no payment provider is configured "
            + "(litemall.order.stripe.enabled=false)";

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public PaymentIntentDraft createIntent(Integer orderId, LitemallMoney amount) {
        // Throw rather than hand back a placeholder id: a stub is what the SPA used to
        // fabricate (pi_stub_<orderId>) and then present to the customer as a payment.
        throw new LitemallPaymentGatewayException(DISABLED);
    }

    @Override
    public PaymentVerification verify(String paymentIntentId, Integer orderId, LitemallMoney expectedAmount) {
        log.warn("Payment verification attempted for order {} while Stripe is disabled — rejecting", orderId);
        return PaymentVerification.rejected(DISABLED);
    }

    @Override
    public RefundOutcome refund(String paymentIntentId, LitemallMoney amount, Integer orderId) {
        log.warn("Refund attempted for order {} while Stripe is disabled — reporting failure "
                + "so the order stays retryable rather than showing money returned", orderId);
        return RefundOutcome.failed(DISABLED);
    }

    @Override
    public PaymentWebhookEvent parseWebhook(String payload, String signatureHeader) {
        throw new LitemallPaymentGatewayException(DISABLED);
    }
}
