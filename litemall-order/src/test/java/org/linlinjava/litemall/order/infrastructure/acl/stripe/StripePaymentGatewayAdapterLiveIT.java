package org.linlinjava.litemall.order.infrastructure.acl.stripe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentDraft;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentVerification;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live TEST-MODE probe of the package-A adapter calls against real Stripe. Skipped unless
 * {@code STRIPE_TEST_SECRET_KEY} (an {@code sk_test_...} key) is set — it never runs in the
 * normal suite, and it can never move money: a test-mode intent is created, inspected,
 * cancelled and inspected again; nothing is ever confirmed.
 *
 * <pre>
 *   STRIPE_TEST_SECRET_KEY=sk_test_... mvn -o -pl litemall-order test -Dtest=StripePaymentGatewayAdapterLiveIT -Dskip.npm=true
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "STRIPE_TEST_SECRET_KEY", matches = "sk_test_.+")
class StripePaymentGatewayAdapterLiveIT {

    private final StripePaymentGatewayAdapter adapter = new StripePaymentGatewayAdapter(
            System.getenv("STRIPE_TEST_SECRET_KEY"), "whsec_unused_in_this_probe", "eur");

    @Test
    void mint_inspect_cancel_inspect_roundTrip() {
        PaymentIntentDraft draft = adapter.createIntent(990001, new LitemallMoney(new BigDecimal("8.58")));
        assertThat(draft.getPaymentIntentId()).startsWith("pi_");

        // Freshly minted, never confirmed: the pre-capture family folds to PENDING and
        // carries our order id back out of metadata.
        PaymentIntentState fresh = adapter.inspect(draft.getPaymentIntentId());
        assertThat(fresh.getStatus()).isEqualTo(PaymentIntentState.Status.PENDING);
        assertThat(fresh.getOrderId()).isEqualTo(990001);
        assertThat(fresh.isSucceededFor(990001)).isFalse();

        // Verification of an unconfirmed intent is a REAL rejection (Stripe answered), not
        // a transient one — the webhook must not 503 on it.
        PaymentVerification verification = adapter.verify(draft.getPaymentIntentId(), 990001,
                new LitemallMoney(new BigDecimal("8.58")));
        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.isRetryable()).isFalse();

        // The sweep's move: cancel at Stripe first. Then it can never capture.
        PaymentIntentState cancelled = adapter.cancelIntent(draft.getPaymentIntentId());
        assertThat(cancelled.getStatus()).isEqualTo(PaymentIntentState.Status.CANCELED);
        assertThat(adapter.inspect(draft.getPaymentIntentId()).getStatus())
                .isEqualTo(PaymentIntentState.Status.CANCELED);

        // Cancelling twice is idempotent (already CANCELED, no second API mutation).
        assertThat(adapter.cancelIntent(draft.getPaymentIntentId()).getStatus())
                .isEqualTo(PaymentIntentState.Status.CANCELED);

        // A refund on an intent that never captured is refused by Stripe and reported as a
        // failure — never as money returned.
        RefundOutcome refund = adapter.refund(draft.getPaymentIntentId(),
                new LitemallMoney(new BigDecimal("8.58")), 990001, draft.getPaymentIntentId());
        assertThat(refund.isOk()).isFalse();
    }

    @Test
    void unknownIntent_isNotTransient() {
        PaymentIntentState state = adapter.inspect("pi_does_not_exist_123");
        // Stripe answered ("no such payment_intent"): nothing can ever capture on it.
        assertThat(state.getStatus()).isEqualTo(PaymentIntentState.Status.CANCELED);

        PaymentVerification verification = adapter.verify("pi_does_not_exist_123", 1,
                new LitemallMoney(new BigDecimal("1.00")));
        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.isRetryable()).isFalse();
    }
}
