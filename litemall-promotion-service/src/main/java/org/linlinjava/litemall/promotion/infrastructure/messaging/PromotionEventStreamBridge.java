package org.linlinjava.litemall.promotion.infrastructure.messaging;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cross-process fan-out of promotion domain events to the single messaging
 * backend (Kafka, mirroring litemall-order). Listens to the {@link
 * LitemallDomainEvent} base type at {@link TransactionPhase#AFTER_COMMIT} so a
 * message is only published once the originating write has durably committed
 * ({@code fallbackExecution = true} keeps non-transactional publishes working).
 *
 * <p>The destination (Kafka topic) is derived from the event name so every
 * vertical — bargain, seckill, coupon, combination — publishes through the same
 * path with no per-event wiring, e.g. {@code COUPON_RECEIVED} →
 * {@code litemall.promotion.coupon.received.v1}. The broker host comes from
 * {@code spring.cloud.stream.kafka.binder.brokers} (config-overridable); nothing
 * is hardcoded here.
 */
@Component
public class PromotionEventStreamBridge {

    private static final Logger logger = LoggerFactory.getLogger(PromotionEventStreamBridge.class);

    private static final String DESTINATION_PREFIX = "litemall.promotion.";

    private final StreamBridge streamBridge;

    public PromotionEventStreamBridge(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(LitemallDomainEvent event) {
        if (event == null || event.getEventName() == null) {
            return;
        }
        String destination = destinationFor(event);
        try {
            streamBridge.send(destination, event);
            logger.debug("Published promotion event {} to {}", event.getEventName(), destination);
        } catch (Exception e) {
            // Never let a messaging outage unwind the already-committed business
            // transaction; the side-effect is best-effort at this phase.
            logger.warn("Failed to publish promotion event {} to {}: {}",
                    event.getEventName(), destination, e.getMessage());
        }
    }

    private String destinationFor(LitemallDomainEvent event) {
        String topic = event.getEventName().toLowerCase().replace('_', '.');
        return DESTINATION_PREFIX + topic + ".v" + event.getSchemaVersion();
    }
}
