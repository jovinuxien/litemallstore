package org.linlinjava.litemall.order.domain.events.eventhandlers;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.events.coupon.LitemallCouponUsedEvent;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCancelledEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.linlinjava.litemall.order.utils.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards in-process domain events to Kafka topics, but only after the
 * publishing transaction commits — so a rolled-back order submit produces no
 * cross-process message. Topic names match the bindings declared in
 * application.yml ({@code spring.cloud.stream.bindings.*.destination}).
 *
 * <p>If an in-process listener also wants to react synchronously to an event
 * (e.g. scheduling a local task that should fire regardless of broker state),
 * it can stay on plain {@code @EventListener}; only cross-process handlers
 * must be AFTER_COMMIT.
 */
@Component
public class LitemallKafkaDomainEventForwarder {

    private static final Logger LOGGER = LoggerFactory.getLogger(LitemallKafkaDomainEventForwarder.class);

    private final StreamBridge streamBridge;

    public LitemallKafkaDomainEventForwarder(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(LitemallOrderCreatedEvent event) {
        forward("orderCreated-out-0", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaymentSucceeded(LitemallOrderPaymentSuccessEvent event) {
        forward("orderPaymentSucceeded-out-0", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(LitemallOrderCancelledEvent event) {
        forward("orderCancelled-out-0", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrouponParticipated(LitemallGrouponParticipatedEvent event) {
        forward("grouponParticipated-out-0", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCouponUsed(LitemallCouponUsedEvent event) {
        forward("couponUsed-out-0", event);
    }

    private void forward(String binding, LitemallDomainEvent event) {
        ensureCorrelationId(event);
        boolean sent = streamBridge.send(binding, event);
        if (!sent) {
            LOGGER.warn("Stream binding {} rejected event {}", binding, event.getEventName());
        }
    }

    /**
     * If the publisher didn't bind a correlationId, take it from the request
     * context (UserContext is populated by the inbound filter). Keeps the
     * default UUID otherwise.
     */
    private void ensureCorrelationId(LitemallDomainEvent event) {
        if (event.getCorrelationId() == null) {
            String fromContext = UserContext.getCorrelationId();
            if (fromContext != null) {
                event.setCorrelationId(fromContext);
            }
        }
    }
}
