package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentTemporarilyUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook (Wave 7, Task A3 — docs/adr-stripe-payments.md).
 *
 * <p><b>The webhook, not the client call, is the authoritative paid signal.</b> The SPA's
 * pay action is a latency optimisation: a customer who closes the tab after Elements
 * confirms still gets a paid order, because Stripe tells us independently.
 *
 * <p><b>This is the only anonymous path added this wave.</b> Stripe cannot present a
 * machine token, so it is listed in {@code litemall.svcsecurity.public-paths} — meaning
 * NOTHING upstream authenticates it and the signature check below IS its authentication.
 * A request that fails signature verification is answered 400 and touches no order.
 *
 * <p>Deliberately takes the RAW body ({@code @RequestBody String}): signature verification
 * runs over the exact bytes Stripe signed, so any re-serialisation through a DTO would
 * break it.
 *
 * <p>Idempotency is Stripe's {@code event.id} recorded in {@code litemall_stripe_event};
 * Stripe retries deliveries, and a retry must not pay an order twice.
 */
@RestController
@RequestMapping("/srv/order/webhook/stripe")
public class StripeWebhookController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(StripeWebhookController.class);

    private final LitemallOrderOrchestratorService orchestrator;

    public StripeWebhookController(LitemallOrderOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Always answers 200 once the signature verifies, even when the event is one we ignore
     * or an order-side failure occurred: a non-2xx makes Stripe retry, and retrying will
     * not fix a business-logic problem — it just floods us. Genuine problems are logged
     * and left to the CJ/reconciliation sweeps and the admin surface.
     *
     * <p>The one non-2xx is a failed signature (400): that request was not Stripe.
     */
    @PostMapping("/order-paid")
    public ResponseEntity<String> orderPaid(@RequestBody String payload,
                                            @RequestHeader(value = "Stripe-Signature", required = false)
                                            String signature) {
        try {
            orchestrator.handleStripeWebhook(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (LitemallPaymentTemporarilyUnavailableException e) {
            // Signature was valid but Stripe itself could not be asked while verifying. The
            // orchestrator's transaction rolled back, so the event claim is released; a
            // non-2xx makes Stripe redeliver with its own backoff — the retry that used to be
            // impossible because the claim survived and the resend was dropped as a duplicate.
            log.warn("Stripe webhook deferred (PSP unavailable): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("retry later");
        } catch (LitemallPaymentGatewayException e) {
            // Bad/absent signature, or the secret is unset. Never say which — an attacker
            // probing the endpoint learns nothing beyond "rejected".
            log.warn("Rejected Stripe webhook delivery: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        } catch (RuntimeException e) {
            // Signature was valid, so this WAS Stripe — swallow and 200 rather than invite
            // a retry storm over a bug on our side.
            log.error("Stripe webhook processing failed after a valid signature", e);
            return ResponseEntity.ok("received");
        }
    }
}
