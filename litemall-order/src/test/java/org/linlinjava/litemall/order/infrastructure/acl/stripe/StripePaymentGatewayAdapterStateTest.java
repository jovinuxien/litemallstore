package org.linlinjava.litemall.order.infrastructure.acl.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure parts of the Stripe adapter that package A relies on: how Stripe's status
 * vocabulary folds into the five states the sweep reasons about, and which exceptions
 * count as "could not ask" versus "Stripe answered no". No network.
 */
class StripePaymentGatewayAdapterStateTest {

    private static PaymentIntent intent(String status, String orderId, Long received) {
        PaymentIntent pi = new PaymentIntent();
        pi.setId("pi_1");
        pi.setStatus(status);
        pi.setCurrency("eur");
        pi.setAmountReceived(received);
        if (orderId != null) {
            pi.setMetadata(Map.of("orderId", orderId));
        }
        return pi;
    }

    @Test
    void succeeded_carriesTheOrderAndTheCapturedAmount() {
        PaymentIntentState state = StripePaymentGatewayAdapter.toState(intent("succeeded", "31", 858L));

        assertThat(state.getStatus()).isEqualTo(PaymentIntentState.Status.SUCCEEDED);
        assertThat(state.isSucceededFor(31)).isTrue();
        assertThat(state.isSucceededFor(32)).isFalse();
        assertThat(state.getAmountReceivedMinor()).isEqualTo(858L);
        assertThat(state.getCurrency()).isEqualTo("eur");
    }

    @Test
    void preCaptureStatuses_foldToPending() {
        for (String s : new String[]{"requires_payment_method", "requires_confirmation", "requires_action", "requires_capture"}) {
            assertThat(StripePaymentGatewayAdapter.toState(intent(s, "31", 0L)).getStatus())
                    .as(s).isEqualTo(PaymentIntentState.Status.PENDING);
        }
    }

    @Test
    void processingAndCanceled_foldOneToOne() {
        assertThat(StripePaymentGatewayAdapter.toState(intent("processing", "31", 0L)).getStatus())
                .isEqualTo(PaymentIntentState.Status.PROCESSING);
        assertThat(StripePaymentGatewayAdapter.toState(intent("canceled", "31", 0L)).getStatus())
                .isEqualTo(PaymentIntentState.Status.CANCELED);
    }

    /** A status this code does not know must never read as "safe to cancel". */
    @Test
    void unknownStatus_isUnavailable_notPending() {
        assertThat(StripePaymentGatewayAdapter.toState(intent("requires_something_new", "31", 0L)).getStatus())
                .isEqualTo(PaymentIntentState.Status.UNAVAILABLE);
        assertThat(StripePaymentGatewayAdapter.toState(null).getStatus())
                .isEqualTo(PaymentIntentState.Status.UNAVAILABLE);
    }

    @Test
    void succeededWithoutOrderMetadata_isNotSucceededForAnyOrder() {
        PaymentIntentState state = StripePaymentGatewayAdapter.toState(intent("succeeded", null, 858L));

        assertThat(state.getStatus()).isEqualTo(PaymentIntentState.Status.SUCCEEDED);
        assertThat(state.getOrderId()).isNull();
        assertThat(state.isSucceededFor(31)).isFalse();
    }

    @Test
    void transportRateLimitAndApiErrors_areTransient_invalidRequestIsNot() {
        assertThat(StripePaymentGatewayAdapter.isTransient(new ApiConnectionException("reset"))).isTrue();
        assertThat(StripePaymentGatewayAdapter.isTransient(
                new InvalidRequestException("No such payment_intent", "id", null, "resource_missing", 404, null))).isFalse();
    }

    @Test
    void disabledAdapter_answersUnavailable_neverAVerdict() {
        DisabledPaymentGatewayAdapter disabled = new DisabledPaymentGatewayAdapter();

        assertThat(disabled.inspect("pi_1").getStatus()).isEqualTo(PaymentIntentState.Status.UNAVAILABLE);
        assertThat(disabled.cancelIntent("pi_1").getStatus()).isEqualTo(PaymentIntentState.Status.UNAVAILABLE);
        assertThat(disabled.refund("pi_1", new LitemallMoney(new BigDecimal("1.00")), 1, "pi_1").isOk()).isFalse();
    }
}
