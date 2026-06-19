package org.linlinjava.litemall.order.domain.events;

import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// After-commit Kafka forwarder. Each handler runs only after the publishing
// transaction commits, so consumers never see events that were rolled back.
// Trade-off: if the JVM crashes between commit and Kafka send, the event is
// lost. A durable order_event_outbox is the fix; deferred per worktree scope.
// Routing: one binding per domain topic; binding -> topic resolved via
// spring.cloud.stream.bindings.* in application.yml.
@Component
public class LitemallKafkaDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LitemallKafkaDomainEventPublisher.class);

    static final String BINDING_ORDER_CREATED = "orderCreated-out-0";
    static final String BINDING_ORDER_PAYMENT_SUCCEEDED = "orderPaymentSucceeded-out-0";
    static final String BINDING_GROUPON_PARTICIPATED = "grouponParticipated-out-0";

    private final StreamBridge streamBridge;

    public LitemallKafkaDomainEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(LitemallOrderCreatedEvent event) {
        forward(BINDING_ORDER_CREATED, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaymentSucceeded(LitemallOrderPaymentSuccessEvent event) {
        forward(BINDING_ORDER_PAYMENT_SUCCEEDED, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrouponParticipated(LitemallGrouponParticipatedEvent event) {
        forward(BINDING_GROUPON_PARTICIPATED, event);
    }

    private void forward(String binding, AbstractLitemallOrderDomainEvent event) {
        try {
            boolean sent = streamBridge.send(binding, event);
            if (!sent) {
                log.warn("StreamBridge.send returned false for binding {} (event {})",
                        binding, event.getClass().getSimpleName());
            }
        } catch (RuntimeException e) {
            log.error("Failed to forward {} to binding {}", event.getClass().getSimpleName(), binding, e);
        }
    }
}
