package org.linlinjava.litemall.order.domain.events;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.springframework.cloud.stream.function.StreamBridge;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Verifies the forwarder routes each event to the right Stream binding.
// Framework-level AFTER_COMMIT semantics (no send on rollback) are guaranteed
// by Spring's @TransactionalEventListener; a Kafka-binder integration test
// covering the commit/rollback boundary is deferred until Phase 6 brings up
// the Kafka container in the existing Testcontainers infra.
class LitemallKafkaDomainEventPublisherTest {

    @Test
    void onOrderCreated_sendsToOrderCreatedBinding() {
        StreamBridge bridge = mock(StreamBridge.class);
        when(bridge.send(any(String.class), any())).thenReturn(true);
        LitemallKafkaDomainEventPublisher publisher = new LitemallKafkaDomainEventPublisher(bridge);

        LitemallOrderCreatedEvent event = new LitemallOrderCreatedEvent(
                new LitemallOrderId(42),
                new LitemallMoney(new BigDecimal("12.34")),
                7, "ORD-7-42");

        publisher.onOrderCreated(event);

        verify(bridge).send(eq(LitemallKafkaDomainEventPublisher.BINDING_ORDER_CREATED), eq(event));
    }

    @Test
    void onOrderPaymentSucceeded_sendsToPaymentBinding() {
        StreamBridge bridge = mock(StreamBridge.class);
        when(bridge.send(any(String.class), any())).thenReturn(true);
        LitemallKafkaDomainEventPublisher publisher = new LitemallKafkaDomainEventPublisher(bridge);

        LitemallOrderPaymentSuccessEvent event = new LitemallOrderPaymentSuccessEvent(
                new LitemallOrderId(42),
                new LitemallMoney(new BigDecimal("12.34")),
                LocalDateTime.now());

        publisher.onOrderPaymentSucceeded(event);

        verify(bridge).send(eq(LitemallKafkaDomainEventPublisher.BINDING_ORDER_PAYMENT_SUCCEEDED), eq(event));
    }

    @Test
    void onGrouponParticipated_sendsToGrouponBinding() {
        StreamBridge bridge = mock(StreamBridge.class);
        when(bridge.send(any(String.class), any())).thenReturn(true);
        LitemallKafkaDomainEventPublisher publisher = new LitemallKafkaDomainEventPublisher(bridge);

        LitemallGrouponParticipatedEvent event = new LitemallGrouponParticipatedEvent(
                new LitemallGrouponId(5),
                new LitemallOrderId(42), 7, LocalDateTime.now());

        publisher.onGrouponParticipated(event);

        verify(bridge).send(eq(LitemallKafkaDomainEventPublisher.BINDING_GROUPON_PARTICIPATED), eq(event));
    }

    @Test
    void send_failureIsLoggedNotThrown() {
        StreamBridge bridge = mock(StreamBridge.class);
        when(bridge.send(any(String.class), any())).thenThrow(new RuntimeException("kafka down"));
        LitemallKafkaDomainEventPublisher publisher = new LitemallKafkaDomainEventPublisher(bridge);

        LitemallOrderCreatedEvent event = new LitemallOrderCreatedEvent(
                new LitemallOrderId(1), new LitemallMoney(new BigDecimal("1.00")), 1, "X");

        // Must not throw — listener errors must not bubble up past Spring's listener.
        publisher.onOrderCreated(event);
    }
}
