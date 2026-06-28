package org.linlinjava.litemall.order.infrastructure.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.dao.EventOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallEventOutbox;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.events.LitemallKafkaDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Transactional-outbox writer. A plain (non-transactional) {@link EventListener} runs
 * SYNCHRONOUSLY when an order domain event is published, which — because every publish
 * happens inside the order's transaction — means this insert joins that same
 * transaction. So the event row is committed atomically with the order state change (or
 * rolled back with it); it can never be lost the way the old AFTER_COMMIT Kafka send
 * could. The {@link OrderEventOutboxRelay} forwards PENDING rows to the broker.
 */
@Component
public class OrderEventOutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(OrderEventOutboxWriter.class);

    private final EventOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OrderEventOutboxWriter(EventOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void capture(AbstractLitemallOrderDomainEvent event) {
        String eventType = event.getClass().getName();
        try {
            LitemallEventOutbox row = new LitemallEventOutbox();
            row.setAggregateType("order");
            row.setAggregateId(resolveAggregateId(event));
            row.setEventType(eventType);
            row.setBinding(LitemallKafkaDomainEventPublisher.bindingFor(eventType));
            row.setPayload(objectMapper.writeValueAsString(event));
            row.setStatus("PENDING");
            row.setAttempts(0);
            row.setCreatedAt(LocalDateTime.now());
            outboxMapper.insert(row);
        } catch (Exception e) {
            // Recording the event is part of the unit of work: if we cannot, fail the
            // transaction loudly rather than silently dropping observability.
            throw new IllegalStateException("Failed to write event to outbox: " + eventType, e);
        }
    }

    /** Best-effort order id for indexing/auditing; the full payload always carries it. */
    private String resolveAggregateId(AbstractLitemallOrderDomainEvent event) {
        try {
            Object id = event.getClass().getMethod("getOrderId").invoke(event);
            if (id instanceof LitemallOrderId orderId && orderId.getId() != null) {
                return String.valueOf(orderId.getId());
            }
        } catch (ReflectiveOperationException ignored) {
            // event without a getOrderId() — leave aggregateId empty
        }
        return "";
    }
}
