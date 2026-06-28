package org.linlinjava.litemall.order.domain.events;

import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// Kafka sender for order domain events. Sending is now driven by the transactional
// OUTBOX relay (OrderEventOutboxRelay), not by an AFTER_COMMIT listener: every event is
// first recorded durably in litemall_event_outbox within the order transaction
// (OrderEventOutboxWriter), then this sender forwards the stored JSON to the broker and
// the relay flips the row to SENT. That closes the old "crash between commit and send
// loses the event" gap.
//
// The typed onXxx(...) methods are retained as direct senders (unit tests call them and
// assert the binding); they are no longer Spring event listeners.
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

    /** Broker binding for an event type (fully-qualified class name), or null if it is not forwarded. */
    public static String bindingFor(String eventType) {
        if (LitemallOrderCreatedEvent.class.getName().equals(eventType)) {
            return BINDING_ORDER_CREATED;
        }
        if (LitemallOrderPaymentSuccessEvent.class.getName().equals(eventType)) {
            return BINDING_ORDER_PAYMENT_SUCCEEDED;
        }
        if (LitemallGrouponParticipatedEvent.class.getName().equals(eventType)) {
            return BINDING_GROUPON_PARTICIPATED;
        }
        return null;
    }

    /**
     * Forward a pre-serialized event payload (the outbox row's JSON) to a binding. Used
     * by the relay so it never has to re-instantiate the event type. Returns true if the
     * broker accepted it; never throws (a broker outage just leaves the row PENDING).
     */
    public boolean sendRaw(String binding, String payloadJson) {
        try {
            boolean sent = streamBridge.send(binding, MessageBuilder
                    .withPayload(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .setHeader("contentType", "application/json")
                    .build());
            if (!sent) {
                log.warn("StreamBridge.send returned false for binding {}", binding);
            }
            return sent;
        } catch (RuntimeException e) {
            log.error("Failed to forward outbox payload to binding {}", binding, e);
            return false;
        }
    }

    public void onOrderCreated(LitemallOrderCreatedEvent event) {
        forward(BINDING_ORDER_CREATED, event);
    }

    public void onOrderPaymentSucceeded(LitemallOrderPaymentSuccessEvent event) {
        forward(BINDING_ORDER_PAYMENT_SUCCEEDED, event);
    }

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
