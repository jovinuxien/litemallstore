package org.linlinjava.litemall.order.infrastructure.messaging.outbox;

import org.linlinjava.litemall.db.dao.EventOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallEventOutbox;
import org.linlinjava.litemall.order.domain.events.LitemallKafkaDomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the transactional outbox to the message broker. Polls PENDING rows, forwards
 * each stored JSON payload to its Stream binding, and flips the row to SENT (or counts a
 * failed attempt, leaving it PENDING for retry until {@code maxAttempts}). At-least-once
 * delivery; consumers must be idempotent.
 *
 * <p>Rows with no binding (event types not forwarded to the broker) are marked SENT
 * immediately — they exist only as a durable audit log.
 */
@Component
public class OrderEventOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderEventOutboxRelay.class);

    private final EventOutboxMapper outboxMapper;
    private final LitemallKafkaDomainEventPublisher sender;

    @Value("${litemall.order.outbox.batch:100}")
    private int batchSize;

    @Value("${litemall.order.outbox.max-attempts:10}")
    private int maxAttempts;

    public OrderEventOutboxRelay(EventOutboxMapper outboxMapper, LitemallKafkaDomainEventPublisher sender) {
        this.outboxMapper = outboxMapper;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${litemall.order.outbox.relay-ms:10000}")
    public void relay() {
        List<LitemallEventOutbox> pending = outboxMapper.selectPending(batchSize);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        int sent = 0;
        for (LitemallEventOutbox row : pending) {
            String binding = row.getBinding();
            if (binding == null || binding.isBlank()) {
                outboxMapper.markSent(row.getId()); // audit-only, nothing to forward
                continue;
            }
            if (sender.sendRaw(binding, row.getPayload())) {
                outboxMapper.markSent(row.getId());
                sent++;
            } else {
                outboxMapper.markFailed(row.getId(), maxAttempts);
            }
        }
        if (sent > 0) {
            log.debug("Outbox relay forwarded {}/{} pending events", sent, pending.size());
        }
    }
}
