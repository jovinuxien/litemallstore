package org.linlinjava.litemall.order.domain.events;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

// In-process publisher: delegates to Spring's ApplicationEventPublisher.
//
// Fan-out: in-process @TransactionalEventListener consumers (e.g. handlers
// under domain/events/eventhandlers/) AND the AFTER_COMMIT Kafka forwarder
// LitemallKafkaDomainEventPublisher both receive the same event through this
// channel. Cross-process listeners must be AFTER_COMMIT so consumers never
// observe events from a rolled-back transaction.
//
// Trade-off: between commit and the Kafka send, a JVM crash loses the event.
// A durable order_event_outbox table (write-in-transaction + scheduled relay)
// is the fix; deferred per scope. See LitemallKafkaDomainEventPublisher.
//
// Registered as the single LitemallDomainEventPublisher bean via an explicit
// @Bean in LitemallDomainEventConfig (NOT @Component, to avoid a duplicate
// definition of the same interface type).
public class LitemallSpringDomainEventPublisher implements LitemallDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public LitemallSpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(LitemallDomainEvent event) {
        // core auto-assigns a random correlationId at construction, so bind the
        // event to the current request's correlation id whenever one is present
        // (preserves cross-process tracing instead of leaving a stray UUID).
        if (event instanceof AbstractLitemallOrderDomainEvent orderEvent) {
            String correlationId = UserContext.getCorrelationId();
            if (correlationId != null) {
                orderEvent.setCorrelationId(correlationId);
            }
        }
        applicationEventPublisher.publishEvent(event);
    }
}
