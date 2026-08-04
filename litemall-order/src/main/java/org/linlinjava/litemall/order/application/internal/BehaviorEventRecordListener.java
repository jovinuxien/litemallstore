package org.linlinjava.litemall.order.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.dao.LitemallUserEventMapper;
import org.linlinjava.litemall.db.domain.LitemallUserEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderRefundedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Behavioral targeting Phase 0 (doc/behavioral-events.md): server-side VALUE
 * signals. Turns committed order lifecycle events into first-party
 * {@code litemall_user_event} rows — {@code purchase} on paid, {@code refund}
 * on refunded — the only two vocabulary types that may NOT originate in the
 * browser (ad blockers, lost redirects, trivial spoofing).
 *
 * <p>Wiring mirrors {@link CustomerMailEnqueueListener}:
 * {@code @TransactionalEventListener(AFTER_COMMIT)} hands off to a 1-thread
 * daemon executor with a bounded queue, and every throwable ends in a WARN —
 * PAYMENT MUST NEVER BLOCK OR FAIL BECAUSE OF ANALYTICS.
 *
 * <p>Rows are written with a DETERMINISTIC event id derived from the order
 * ({@code purchase:<orderId>} / {@code refund:<orderId>:<amount>}), so event
 * re-deliveries and listener retries are absorbed by the {@code INSERT IGNORE}
 * on {@code UNIQUE(event_id)}. visitor_id/session_id stay NULL by contract
 * (the Stripe-webhook path has no browser cookie); the row joins through
 * {@code user_id} + {@code litemall_visitor_identity} at query time.
 */
@Component
public class BehaviorEventRecordListener {

    private static final Logger log = LoggerFactory.getLogger(BehaviorEventRecordListener.class);

    /** Plain mapper on purpose — payload JSON must stay portable, not module-flavored. */
    private static final ObjectMapper PAYLOAD_JSON = new ObjectMapper();

    private final LitemallOrderRepository orderRepository;
    private final LitemallOrderGoodsRepository orderGoodsRepository;
    private final LitemallUserEventMapper userEventMapper;
    private final boolean enabled;

    /** 1 daemon worker, bounded queue: analytics writes never back up payments. */
    private final ThreadPoolExecutor executor;

    public BehaviorEventRecordListener(LitemallOrderRepository orderRepository,
                                       LitemallOrderGoodsRepository orderGoodsRepository,
                                       LitemallUserEventMapper userEventMapper,
                                       @Value("${litemall.tracking.enabled:true}") boolean enabled) {
        this.orderRepository = orderRepository;
        this.orderGoodsRepository = orderGoodsRepository;
        this.userEventMapper = userEventMapper;
        this.enabled = enabled;
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                runnable -> {
                    Thread thread = new Thread(runnable, "behavior-event-record-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> log.warn("Behavior-event queue full (size {}); dropping one task — "
                        + "the missing purchase/refund row can be reconciled from litemall_order", pool.getQueue().size()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderPaid(LitemallOrderPaidEvent event) {
        submit(event.getOrderId().getId(), this::recordPurchase);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderRefunded(LitemallOrderRefundedEvent event) {
        submit(event.getOrderId().getId(), this::recordRefund);
    }

    private void submit(Integer orderId, java.util.function.Consumer<LitemallOrderAggregate> record) {
        if (!enabled) {
            return; // kill-switch: no thread hop, no DB reads, no rows
        }
        try {
            executor.execute(() -> recordFor(orderId, record));
        } catch (RuntimeException e) {
            log.warn("Behavior-event submit failed for order {}: {}", orderId, e.getMessage());
        }
    }

    private void recordFor(Integer orderId, java.util.function.Consumer<LitemallOrderAggregate> record) {
        try {
            LitemallOrderAggregate order = orderRepository.findById(new LitemallOrderId(orderId)).orElse(null);
            if (order == null) {
                log.warn("Behavior event skipped: order {} not found after commit", orderId);
                return;
            }
            record.accept(order);
        } catch (Throwable t) {
            log.warn("Behavior event record failed for order {}: {}", orderId, t.getMessage());
        }
    }

    private void recordPurchase(LitemallOrderAggregate order) {
        Integer orderId = order.getOrderId().getId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderSn", order.getOrderSn());
        payload.put("total", order.getActualPrice() == null ? null : order.getActualPrice().getAmount());
        payload.put("items", itemLines(order.getOrderId()));
        insert(order, LitemallUserEvent.TYPE_PURCHASE, deterministicId("purchase:" + orderId), payload);
    }

    private void recordRefund(LitemallOrderAggregate order) {
        Integer orderId = order.getOrderId().getId();
        BigDecimal amount = order.getRefundAmount() != null ? order.getRefundAmount().getAmount()
                : order.getActualPrice() == null ? null : order.getActualPrice().getAmount();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderSn", order.getOrderSn());
        payload.put("amount", amount);
        insert(order, LitemallUserEvent.TYPE_REFUND, deterministicId("refund:" + orderId + ":" + amount), payload);
    }

    private List<Map<String, Object>> itemLines(LitemallOrderId orderId) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            for (LitemallOrderGoodsAggregate line : orderGoodsRepository.findByOId(orderId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("goodsId", line.getGoodsId() == null ? null : line.getGoodsId().getId());
                item.put("productId", line.getProductId() == null ? null : line.getProductId().getId());
                item.put("qty", line.getNumber());
                item.put("price", line.getPrice() == null ? null : line.getPrice().getAmount());
                items.add(item);
            }
        } catch (RuntimeException e) {
            // A purchase row without item lines still beats no row at all.
            log.warn("Behavior event item lines unavailable for order {}: {}", orderId.getId(), e.getMessage());
        }
        return items;
    }

    private void insert(LitemallOrderAggregate order, String type, String eventId, Map<String, Object> payload) {
        LitemallUserEvent row = new LitemallUserEvent();
        row.setEventId(eventId);
        row.setUserId(order.getUserId() == null ? null : order.getUserId().getId());
        row.setEventType(type);
        row.setOrigin(LitemallUserEvent.ORIGIN_SERVER);
        LocalDateTime now = LocalDateTime.now();
        row.setOccurredAt(now);
        row.setReceivedAt(now);
        row.setPayload(serialize(payload));
        userEventMapper.insertIgnore(row);
    }

    private static String serialize(Map<String, Object> payload) {
        try {
            return PAYLOAD_JSON.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    /** Type-3 UUID from the order key: replays produce the same id, INSERT IGNORE absorbs them. */
    private static String deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
