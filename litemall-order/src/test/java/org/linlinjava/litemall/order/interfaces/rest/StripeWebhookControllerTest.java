package org.linlinjava.litemall.order.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentTemporarilyUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * The three answers the webhook gives Stripe, and why: 200 for handled-or-our-bug (no retry
 * storm), 400 for not-Stripe, and — new with package A — 503 when Stripe itself could not
 * be asked, which is the one case where a redelivery is exactly what we want.
 */
class StripeWebhookControllerTest {

    private final LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
    private final StripeWebhookController controller = new StripeWebhookController(orchestrator);

    @Test
    void handled_is200() {
        ResponseEntity<String> response = controller.orderPaid("{}", "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void badSignature_is400() {
        doThrow(new LitemallPaymentGatewayException("bad sig")).when(orchestrator).handleStripeWebhook(anyString(), anyString());

        ResponseEntity<String> response = controller.orderPaid("{}", "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pspUnavailable_is503_soStripeRedelivers() {
        doThrow(new LitemallPaymentTemporarilyUnavailableException("stripe down"))
                .when(orchestrator).handleStripeWebhook(anyString(), anyString());

        ResponseEntity<String> response = controller.orderPaid("{}", "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void ourOwnBug_isStill200() {
        doThrow(new IllegalStateException("npe-ish")).when(orchestrator).handleStripeWebhook(anyString(), anyString());

        ResponseEntity<String> response = controller.orderPaid("{}", "sig");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
