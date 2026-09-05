package org.linlinjava.litemall.order.infrastructure.acl.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentDraft;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentVerification;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentWebhookEvent;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Stripe implementation of the PSP seam (Wave 7, Task A — docs/adr-stripe-payments.md).
 *
 * <p>Constructed by {@code FulfillmentSeamsConfiguration} rather than component-scanned,
 * so exactly one {@link PaymentGatewayPort} bean exists (module convention).
 *
 * <p>The API key travels in per-call {@link RequestOptions} rather than the global static
 * {@code Stripe.apiKey}. litemall-core's {@code StripeConfig} sets that global from its own
 * committed test keys at boot; relying on it would make which key we charge with depend on
 * bean-init order across modules.
 */
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGatewayAdapter.class);

    /** Stripe's own metadata key for the order this intent pays. Asserted back at verify. */
    static final String METADATA_ORDER_ID = "orderId";

    private final RequestOptions requestOptions;
    private final String webhookSecret;
    private final String currency;

    public StripePaymentGatewayAdapter(String secretKey, String webhookSecret, String currency) {
        this.requestOptions = RequestOptions.builder().setApiKey(secretKey).build();
        this.webhookSecret = webhookSecret;
        this.currency = currency.toLowerCase();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public PaymentIntentDraft createIntent(Integer orderId, LitemallMoney amount) {
        long minor = toMinorUnits(amount);
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(minor)
                    .setCurrency(currency)
                    .putMetadata(METADATA_ORDER_ID, String.valueOf(orderId))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();
            PaymentIntent intent = PaymentIntent.create(params, requestOptions);
            log.info("Created PaymentIntent {} for order {} ({} {})",
                    intent.getId(), orderId, minor, currency);
            return new PaymentIntentDraft(intent.getId(), intent.getClientSecret(), minor, currency);
        } catch (StripeException e) {
            // No fallback: a stub id here is indistinguishable from a real payment later.
            throw new LitemallPaymentGatewayException(
                    "Could not start a card payment for order " + orderId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentVerification verify(String paymentIntentId, Integer orderId, LitemallMoney expectedAmount) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return PaymentVerification.rejected("no PaymentIntent id supplied");
        }

        PaymentIntent intent;
        try {
            intent = PaymentIntent.retrieve(paymentIntentId, requestOptions);
        } catch (StripeException e) {
            log.warn("Could not retrieve PaymentIntent {} for order {}: {}",
                    paymentIntentId, orderId, e.getMessage());
            if (isTransient(e)) {
                // Stripe did not answer. Still a rejection (fail closed), but flagged so the
                // webhook releases its event claim and lets Stripe redeliver (F2).
                return PaymentVerification.unavailable("PaymentIntent could not be retrieved: " + e.getMessage());
            }
            // "no such payment_intent" and friends — an id Stripe does not know is not a payment.
            return PaymentVerification.rejected("PaymentIntent could not be retrieved: " + e.getMessage());
        }

        if (intent == null) {
            return PaymentVerification.rejected("PaymentIntent " + paymentIntentId + " does not exist");
        }

        // Every assertion below must hold. Order matters only for the message.
        if (!"succeeded".equals(intent.getStatus())) {
            return PaymentVerification.rejected(
                    "PaymentIntent " + paymentIntentId + " is '" + intent.getStatus() + "', not 'succeeded'");
        }

        long expectedMinor = toMinorUnits(expectedAmount);
        Long received = intent.getAmountReceived();
        if (received == null || received != expectedMinor) {
            // The tampering signal: a real Elements flow cannot produce this.
            log.warn("PaymentIntent {} amount mismatch for order {}: received={} expected={}",
                    paymentIntentId, orderId, received, expectedMinor);
            return PaymentVerification.rejected(
                    "amount mismatch: received " + received + ", expected " + expectedMinor);
        }

        if (!currency.equalsIgnoreCase(intent.getCurrency())) {
            return PaymentVerification.rejected(
                    "currency mismatch: intent is '" + intent.getCurrency() + "', expected '" + currency + "'");
        }

        // Binds the intent to THIS order. Without it, a genuine intent for a cheap order
        // would verify against an expensive one whenever the amounts happened to match.
        String metaOrderId = intent.getMetadata() == null ? null : intent.getMetadata().get(METADATA_ORDER_ID);
        if (metaOrderId == null || !metaOrderId.equals(String.valueOf(orderId))) {
            log.warn("PaymentIntent {} carries orderId '{}' but was presented for order {}",
                    paymentIntentId, metaOrderId, orderId);
            return PaymentVerification.rejected(
                    "PaymentIntent belongs to order '" + metaOrderId + "', not " + orderId);
        }

        return PaymentVerification.accepted();
    }

    @Override
    public RefundOutcome refund(String paymentIntentId, LitemallMoney amount, Integer orderId) {
        return refund(paymentIntentId, amount, orderId, null);
    }

    @Override
    public RefundOutcome refund(String paymentIntentId, LitemallMoney amount, Integer orderId,
                                String idempotencyScope) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return RefundOutcome.failed("order " + orderId + " has no recorded PaymentIntent to reverse");
        }
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(toMinorUnits(amount))
                    // Stripe dedupes on this key, so a retried admin approval reverses once.
                    .putMetadata(METADATA_ORDER_ID, String.valueOf(orderId))
                    .build();
            String key = idempotencyScope == null || idempotencyScope.isBlank()
                    ? "refund-order-" + orderId
                    : "refund-order-" + orderId + "-" + idempotencyScope;
            RequestOptions idempotent = requestOptions.toBuilder()
                    .setIdempotencyKey(key)
                    .build();
            Refund refund = Refund.create(params, idempotent);
            log.info("Refunded {} on PaymentIntent {} for order {} (refund {})",
                    amount.getAmount(), paymentIntentId, orderId, refund.getId());
            return RefundOutcome.succeeded(refund.getId());
        } catch (StripeException e) {
            log.error("Refund FAILED at Stripe for order {} (intent {}): {}",
                    orderId, paymentIntentId, e.getMessage());
            return RefundOutcome.failed(e.getMessage());
        }
    }

    @Override
    public PaymentIntentState inspect(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return PaymentIntentState.unavailable("no PaymentIntent id");
        }
        try {
            return toState(PaymentIntent.retrieve(paymentIntentId, requestOptions));
        } catch (StripeException e) {
            log.warn("Could not inspect PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            if (isTransient(e)) {
                return PaymentIntentState.unavailable(e.getMessage());
            }
            // Stripe answered and does not know this id: nothing can capture on it.
            return PaymentIntentState.of(PaymentIntentState.Status.CANCELED, null, 0L, null,
                    "unknown to Stripe: " + e.getMessage());
        }
    }

    @Override
    public PaymentIntentState cancelIntent(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return PaymentIntentState.unavailable("no PaymentIntent id");
        }
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, requestOptions);
            PaymentIntentState before = toState(intent);
            if (before.is(PaymentIntentState.Status.CANCELED)) {
                return before;
            }
            if (!before.is(PaymentIntentState.Status.PENDING)) {
                // succeeded / processing: money is captured or in flight — not ours to cancel.
                return before;
            }
            PaymentIntent cancelled = intent.cancel(requestOptions);
            log.info("Cancelled PaymentIntent {} ({} -> {})", paymentIntentId,
                    intent.getStatus(), cancelled.getStatus());
            return toState(cancelled);
        } catch (StripeException e) {
            log.warn("Could not cancel PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            if (isTransient(e)) {
                return PaymentIntentState.unavailable(e.getMessage());
            }
            // Stripe refused (typically: status moved under us). Re-read so the caller acts
            // on what the intent IS, not on what we tried to make it.
            return inspect(paymentIntentId);
        }
    }

    /**
     * Stripe's status vocabulary folded to the five states the order module reasons about.
     * Anything unrecognised is treated as PENDING-like only if it is one of the documented
     * pre-capture statuses; unknown strings are reported UNAVAILABLE so a library upgrade
     * that adds a status can never be misread as "safe to cancel".
     */
    static PaymentIntentState toState(PaymentIntent intent) {
        if (intent == null) {
            return PaymentIntentState.unavailable("null PaymentIntent");
        }
        String status = intent.getStatus() == null ? "" : intent.getStatus();
        PaymentIntentState.Status folded;
        switch (status) {
            case "succeeded" -> folded = PaymentIntentState.Status.SUCCEEDED;
            case "processing" -> folded = PaymentIntentState.Status.PROCESSING;
            case "canceled" -> folded = PaymentIntentState.Status.CANCELED;
            case "requires_payment_method", "requires_confirmation", "requires_action", "requires_capture" ->
                    folded = PaymentIntentState.Status.PENDING;
            default -> {
                return PaymentIntentState.unavailable("unrecognised PaymentIntent status '" + status + "'");
            }
        }
        Integer orderId = null;
        Map<String, String> metadata = intent.getMetadata();
        if (metadata != null && metadata.get(METADATA_ORDER_ID) != null) {
            try {
                orderId = Integer.valueOf(metadata.get(METADATA_ORDER_ID));
            } catch (NumberFormatException ignored) {
                // reported as null: the caller's orderId match then fails, which is the safe side
            }
        }
        long received = intent.getAmountReceived() == null ? 0L : intent.getAmountReceived();
        return PaymentIntentState.of(folded, orderId, received, intent.getCurrency(), status);
    }

    /**
     * A failure that says nothing about the intent: connection, rate limit, Stripe 5xx,
     * or our own credentials (a config problem is not a verdict on the customer's money).
     * {@code InvalidRequestException} (unknown id, bad parameters) is a real answer.
     */
    static boolean isTransient(StripeException e) {
        if (e instanceof com.stripe.exception.InvalidRequestException) {
            return false;
        }
        if (e instanceof com.stripe.exception.CardException) {
            return false;
        }
        return true;
    }

    @Override
    public PaymentWebhookEvent parseWebhook(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Fail closed: without the secret we cannot tell Stripe from anyone else.
            throw new LitemallPaymentGatewayException(
                    "Stripe webhook secret is not configured — refusing to trust the payload");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new LitemallPaymentGatewayException("missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new LitemallPaymentGatewayException("Stripe signature verification failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new LitemallPaymentGatewayException("unparseable Stripe webhook payload: " + e.getMessage(), e);
        }

        PaymentIntent intent = extractPaymentIntent(event);
        if (intent == null) {
            // Signature was valid, so this really is Stripe — just an event carrying
            // something other than a PaymentIntent. Not an error; the caller ignores it.
            return new PaymentWebhookEvent(event.getId(), event.getType(), null, null, 0L, null);
        }

        Integer orderId = null;
        Map<String, String> metadata = intent.getMetadata();
        if (metadata != null && metadata.get(METADATA_ORDER_ID) != null) {
            try {
                orderId = Integer.valueOf(metadata.get(METADATA_ORDER_ID));
            } catch (NumberFormatException e) {
                log.warn("Stripe event {} carries a non-numeric orderId '{}'",
                        event.getId(), metadata.get(METADATA_ORDER_ID));
            }
        }

        return new PaymentWebhookEvent(
                event.getId(),
                event.getType(),
                intent.getId(),
                orderId,
                intent.getAmountReceived() == null ? 0L : intent.getAmountReceived(),
                intent.getCurrency());
    }

    /**
     * Stripe's typed deserializer returns empty when the account's API version differs from
     * the library's, which would silently turn every event into "not a PaymentIntent".
     * Fall back to the unsafe deserializer so a version skew surfaces as a warning rather
     * than as payments that never land.
     */
    private PaymentIntent extractPaymentIntent(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        var object = deserializer.getObject();
        if (object.isPresent()) {
            return object.get() instanceof PaymentIntent pi ? pi : null;
        }
        try {
            return deserializer.deserializeUnsafe() instanceof PaymentIntent pi ? pi : null;
        } catch (Exception e) {
            log.warn("Stripe event {} ({}) could not be deserialized — likely an API version skew: {}",
                    event.getId(), event.getType(), e.getMessage());
            return null;
        }
    }

    /**
     * LitemallMoney is normalised to exactly scale 2 in its constructor, so shifting two
     * places is lossless and {@code longValueExact} cannot silently truncate a cent.
     */
    private static long toMinorUnits(LitemallMoney money) {
        BigDecimal amount = money.getAmount();
        return amount.movePointRight(2).longValueExact();
    }
}
