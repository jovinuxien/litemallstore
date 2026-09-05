package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * What the unpaid-order sweep does with ONE due order (plan-order-lifecycle-e2e.md,
 * package A, finding F1).
 *
 * <p>Until 2026-09-05 the sweep cancelled every due CREATED order blind. The Stripe
 * PaymentIntent it had minted was never cancelled and its id was not even stored, so a
 * confirmation after minute 30 — or any asynchronous method (SEPA sits in {@code processing}
 * for days; Klarna/iDEAL/3-DS return through a redirect that only the webhook completes) —
 * captured the customer's money for an order already at SYSTEM_CANCELED, and the webhook
 * then threw into a deliberate 200. Charged, cancelled, no refund.
 *
 * <p>Now the sweep asks the PSP first and the intent decides:
 * <ul>
 *   <li>{@code SUCCEEDED} for this order → the order is PAID, not cancelled (settled through
 *       the same path the webhook uses);</li>
 *   <li>{@code PROCESSING} → money is in flight; the task is DEFERRED, the order stays CREATED;</li>
 *   <li>{@code UNAVAILABLE} → we could not ask; DEFER, never cancel blind;</li>
 *   <li>{@code PENDING} / {@code CANCELED} → the intent is cancelled AT THE PSP first (a late
 *       confirmation can no longer capture), then the order is cancelled as before. If the
 *       PSP refuses because the intent succeeded or is processing meanwhile, that answer wins.</li>
 * </ul>
 * An order that never minted an intent (customer never reached the payment page, wallet
 * checkout) has nothing at the PSP and is cancelled as before.
 *
 * <p>Transactions: the sweep holds {@code FOR UPDATE SKIP LOCKED} claims for its whole batch,
 * so every mutation here goes through a proxy with REQUIRES_NEW ({@code autoCancelOrder},
 * {@code settleVerifiedPspPayment}) and a per-order failure never poisons the claim
 * transaction. The orchestrator is injected {@link Lazy} because it depends on the sweep
 * scheduler (the cancel path retires tasks through it) — the same cycle that kept the
 * cancel-time retirement out of the service class in August.
 */
@Component
public class UnpaidOrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderReconciler.class);

    public static final String CHANGE_TYPE_PAYMENT_RECONCILED = "payment_reconciled";

    /** What the sweep should do with the task row afterwards. */
    public static final class Outcome {
        public enum Kind { CANCELLED, PAID, RETIRED, DEFERRED }

        private final Kind kind;
        private final LocalDateTime deferUntil;

        private Outcome(Kind kind, LocalDateTime deferUntil) {
            this.kind = kind;
            this.deferUntil = deferUntil;
        }

        static Outcome cancelled() { return new Outcome(Kind.CANCELLED, null); }
        static Outcome paid() { return new Outcome(Kind.PAID, null); }
        static Outcome retired() { return new Outcome(Kind.RETIRED, null); }
        static Outcome deferred(LocalDateTime until) { return new Outcome(Kind.DEFERRED, until); }

        public Kind getKind() { return kind; }
        /** Non-null only for {@link Kind#DEFERRED}. */
        public LocalDateTime getDeferUntil() { return deferUntil; }
        public boolean isDeferred() { return kind == Kind.DEFERRED; }
    }

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderServiceImpl orderServiceImpl;
    private final LitemallOrderOrchestratorService orchestrator;
    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final int processingDeferMinutes;
    private final int unavailableDeferMinutes;

    public UnpaidOrderReconciler(LitemallOrderRepository orderRepository,
                                 LitemallOrderServiceImpl orderServiceImpl,
                                 @Lazy LitemallOrderOrchestratorService orchestrator,
                                 LitemallOrderStatusHistoryRepository statusHistoryRepository,
                                 PaymentGatewayPort paymentGatewayPort,
                                 @Value("${litemall.order.unpaid-reconcile.processing-defer-minutes:60}")
                                 int processingDeferMinutes,
                                 @Value("${litemall.order.unpaid-reconcile.unavailable-defer-minutes:5}")
                                 int unavailableDeferMinutes) {
        this.orderRepository = orderRepository;
        this.orderServiceImpl = orderServiceImpl;
        this.orchestrator = orchestrator;
        this.statusHistoryRepository = statusHistoryRepository;
        this.paymentGatewayPort = paymentGatewayPort;
        this.processingDeferMinutes = Math.max(1, processingDeferMinutes);
        this.unavailableDeferMinutes = Math.max(1, unavailableDeferMinutes);
    }

    /**
     * @throws NoSuchElementException when the order row is gone — the sweep retires the task
     */
    public Outcome resolve(LitemallOrderId orderId) {
        LitemallOrderAggregate order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));
        if (order.getOrderStatus() != LitemallOrderStatus.CREATED) {
            // Paid or cancelled by someone else since the task was scheduled. Nothing to do
            // and nothing to wait for.
            log.info("Unpaid-order task for order {} retired: already {}", orderId.getId(), order.getOrderStatus());
            return Outcome.retired();
        }

        String intentId = order.getPaymentIntentId();
        if (intentId == null || intentId.isBlank()) {
            orderServiceImpl.autoCancelOrder(orderId, "auto-cancelled: unpaid timeout");
            return Outcome.cancelled();
        }

        PaymentIntentState state = paymentGatewayPort.inspect(intentId);
        return act(orderId, intentId, state, /*cancelAttempted*/ false);
    }

    private Outcome act(LitemallOrderId orderId, String intentId, PaymentIntentState state, boolean cancelAttempted) {
        switch (state.getStatus()) {
            case SUCCEEDED -> {
                if (!state.isSucceededFor(orderId.getId())) {
                    // An intent recorded on this row but tagged for another order cannot happen
                    // through our own mint path. Refuse to act on it either way.
                    log.error("Unpaid-order sweep: PaymentIntent {} on order {} reports SUCCEEDED for order {} — "
                            + "deferring, needs a human", intentId, orderId.getId(), state.getOrderId());
                    return Outcome.deferred(LocalDateTime.now().plusMinutes(unavailableDeferMinutes));
                }
                log.info("Unpaid-order sweep: order {} was PAID at the PSP (intent {}) — settling instead of cancelling",
                        orderId.getId(), intentId);
                orchestrator.settleVerifiedPspPayment(orderId, intentId);
                recordHop(orderId, "Payment " + intentId + " confirmed with the payment provider during the unpaid-order sweep");
                return Outcome.paid();
            }
            case PROCESSING -> {
                log.info("Unpaid-order sweep: order {} has payment {} in flight ({}) — deferring {} min",
                        orderId.getId(), intentId, state.getDetail(), processingDeferMinutes);
                return Outcome.deferred(LocalDateTime.now().plusMinutes(processingDeferMinutes));
            }
            case UNAVAILABLE -> {
                log.warn("Unpaid-order sweep: could not inspect payment {} for order {} ({}) — deferring {} min, not cancelling",
                        intentId, orderId.getId(), state.getDetail(), unavailableDeferMinutes);
                return Outcome.deferred(LocalDateTime.now().plusMinutes(unavailableDeferMinutes));
            }
            case PENDING -> {
                if (cancelAttempted) {
                    // cancelIntent answered PENDING after we asked it to cancel: the PSP did not
                    // do it and did not say why. Not knowledge — defer.
                    return Outcome.deferred(LocalDateTime.now().plusMinutes(unavailableDeferMinutes));
                }
                PaymentIntentState after = paymentGatewayPort.cancelIntent(intentId);
                return act(orderId, intentId, after, true);
            }
            case CANCELED -> {
                orderServiceImpl.autoCancelOrder(orderId, "auto-cancelled: unpaid timeout");
                return Outcome.cancelled();
            }
            default -> throw new IllegalStateException("unhandled PaymentIntentState " + state.getStatus());
        }
    }

    private void recordHop(LitemallOrderId orderId, String message) {
        try {
            statusHistoryRepository.record(new LitemallOrderStatusChange(orderId,
                    LitemallOrderStatus.PAID, LitemallOrderStatus.PAID,
                    CHANGE_TYPE_PAYMENT_RECONCILED, message, "system", LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("could not record reconciliation hop for order {}: {}", orderId.getId(), e.getMessage());
        }
    }
}
