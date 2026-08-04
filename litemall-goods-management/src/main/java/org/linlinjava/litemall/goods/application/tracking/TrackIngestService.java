package org.linlinjava.litemall.goods.application.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.dao.LitemallUserEventMapper;
import org.linlinjava.litemall.db.domain.LitemallConsentRecord;
import org.linlinjava.litemall.db.domain.LitemallUserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

/**
 * Behavioral-event ingest (Phase 0 — doc/behavioral-events.md).
 *
 * <p>The write path is fire-and-forget by contract: {@link #offerBatch} validates,
 * normalizes and drops onto bounded in-memory queues, returning before any DB
 * work; a {@code @Scheduled} drainer multi-row-inserts every couple of seconds.
 * Full queues DROP (with a WARN) rather than back up request threads, and a
 * failed insert drops its batch — at-most-once is acceptable for analytics,
 * slowing the store is not. Idempotency lives in the DB ({@code INSERT IGNORE}
 * on {@code UNIQUE(event_id)}), so client retries never duplicate.
 *
 * <p>This service only ever writes {@code origin=1} (client) rows and silently
 * drops server-only types ({@code purchase}/{@code refund}) — those originate in
 * the order service. Identity stitching rides the same drain: a batch carrying
 * both the edge-verified user and visitor ids enqueues an {@code INSERT IGNORE}
 * link, which is how login and guest-claim stitch with no auth-path changes.
 */
@Service
public class TrackIngestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackIngestService.class);

    /** Client-emittable vocabulary; purchase/refund are server-only (BehaviorEventRecordListener) and dropped here. */
    static final Set<String> CLIENT_TYPES = Set.of(
            LitemallUserEvent.TYPE_PAGE_VIEW,
            LitemallUserEvent.TYPE_VIEW_ITEM,
            LitemallUserEvent.TYPE_VIEW_CATEGORY,
            LitemallUserEvent.TYPE_SEARCH,
            LitemallUserEvent.TYPE_CLICK_RESULT,
            LitemallUserEvent.TYPE_ADD_TO_CART,
            LitemallUserEvent.TYPE_REMOVE_FROM_CART,
            LitemallUserEvent.TYPE_BEGIN_CHECKOUT);

    static final int BATCH_MAX_EVENTS = 50;
    static final int PAYLOAD_MAX_CHARS = 4096;
    private static final long CLOCK_PAST_TOLERANCE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long CLOCK_FUTURE_TOLERANCE_MS = 5L * 60 * 1000;
    private static final Pattern UUID_36 = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Plain mapper on purpose — payload JSON must not inherit the module's LocalDateTime-as-array quirk. */
    private static final ObjectMapper PAYLOAD_JSON = new ObjectMapper();

    private final LitemallUserEventMapper eventMapper;
    private final boolean enabled;
    private final int insertBatch;

    private final LinkedBlockingQueue<LitemallUserEvent> eventQueue;
    private final LinkedBlockingQueue<LitemallConsentRecord> consentQueue = new LinkedBlockingQueue<>(1000);
    /** Pending stitch pair carried as (visitorId, userId); boxed in an Object[] to keep the queue simple. */
    private final LinkedBlockingQueue<Object[]> stitchQueue = new LinkedBlockingQueue<>(1000);

    public TrackIngestService(LitemallUserEventMapper eventMapper,
                              @Value("${litemall.tracking.enabled:true}") boolean enabled,
                              @Value("${litemall.tracking.queue-capacity:20000}") int queueCapacity,
                              @Value("${litemall.tracking.insert-batch:200}") int insertBatch) {
        this.eventMapper = eventMapper;
        this.enabled = enabled;
        this.insertBatch = Math.max(1, insertBatch);
        this.eventQueue = new LinkedBlockingQueue<>(Math.max(100, queueCapacity));
    }

    /** Result of a batch offer — what the endpoint reports back, nothing more. */
    public record IngestResult(int accepted, int dropped) {
    }

    /**
     * Validate + normalize + enqueue a client batch. Never throws, never touches
     * the DB; anything invalid is silently counted as dropped.
     */
    public IngestResult offerBatch(List<TrackEventDto> events, IngestContext ctx) {
        if (!enabled || events == null || events.isEmpty()) {
            return new IngestResult(0, events == null ? 0 : events.size());
        }
        LocalDateTime receivedAt = LocalDateTime.now();
        int accepted = 0;
        int dropped = 0;
        int considered = 0;
        for (TrackEventDto dto : events) {
            if (considered++ >= BATCH_MAX_EVENTS) {
                dropped += events.size() - BATCH_MAX_EVENTS;
                break;
            }
            LitemallUserEvent row = normalize(dto, ctx, receivedAt);
            if (row != null && eventQueue.offer(row)) {
                accepted++;
            } else {
                if (row != null) {
                    LOGGER.warn("tracking event queue full — event dropped");
                }
                dropped++;
            }
        }
        if (accepted > 0 && ctx.getUserId() != null && ctx.getVisitorId() != null
                && !stitchQueue.offer(new Object[]{ctx.getVisitorId(), ctx.getUserId(), receivedAt})) {
            LOGGER.warn("tracking stitch queue full — identity link dropped");
        }
        return new IngestResult(accepted, dropped);
    }

    /** Enqueue a consent audit row. Recorded even without a visitor id (denials have none). */
    public void offerConsent(String choice, String scope, Long occurredAtMillis, IngestContext ctx) {
        if (!enabled) {
            return;
        }
        LocalDateTime receivedAt = LocalDateTime.now();
        LitemallConsentRecord record = new LitemallConsentRecord();
        record.setVisitorId(normalizeUuid(ctx.getVisitorId()));
        record.setUserId(ctx.getUserId());
        record.setChoice(choice);
        record.setScope(truncate(scope, 32));
        record.setOccurredAt(clampOccurredAt(occurredAtMillis, receivedAt));
        record.setReceivedAt(receivedAt);
        record.setCountryCode(ctx.getCountryCode());
        if (!consentQueue.offer(record)) {
            LOGGER.warn("tracking consent queue full — record dropped");
        }
    }

    /**
     * Drain everything queued since the last tick. Every failure path logs and
     * drops — the drainer must survive any DB hiccup and never rethrow into the
     * scheduler.
     */
    @Scheduled(fixedDelayString = "${litemall.tracking.drain-ms:2000}")
    public void drain() {
        try {
            drainEvents();
            drainStitches();
            drainConsents();
        } catch (Exception e) {
            LOGGER.warn("tracking drain tick failed: {}", e.getMessage());
        }
    }

    private void drainEvents() {
        while (!eventQueue.isEmpty()) {
            List<LitemallUserEvent> batch = new ArrayList<>(insertBatch);
            eventQueue.drainTo(batch, insertBatch);
            if (batch.isEmpty()) {
                return;
            }
            try {
                eventMapper.batchInsertIgnore(batch);
            } catch (Exception e) {
                LOGGER.warn("tracking batch insert failed ({} events dropped): {}", batch.size(), e.getMessage());
            }
        }
    }

    private void drainStitches() {
        Object[] pair;
        while ((pair = stitchQueue.poll()) != null) {
            try {
                eventMapper.insertIdentityLinkIgnore((String) pair[0], (Integer) pair[1], (LocalDateTime) pair[2]);
            } catch (Exception e) {
                LOGGER.warn("visitor identity link insert failed: {}", e.getMessage());
            }
        }
    }

    private void drainConsents() {
        LitemallConsentRecord record;
        while ((record = consentQueue.poll()) != null) {
            try {
                eventMapper.insertConsentRecord(record);
            } catch (Exception e) {
                LOGGER.warn("consent record insert failed: {}", e.getMessage());
            }
        }
    }

    /** Null = drop this event. Applies the whole validation contract for client rows. */
    private LitemallUserEvent normalize(TrackEventDto dto, IngestContext ctx, LocalDateTime receivedAt) {
        if (dto == null) {
            return null;
        }
        String eventId = normalizeUuid(dto.getEventId());
        String type = dto.getType();
        if (eventId == null || type == null || !CLIENT_TYPES.contains(type) || !hasRequiredFields(dto, type)) {
            return null;
        }
        LitemallUserEvent row = new LitemallUserEvent();
        row.setEventId(eventId);
        row.setVisitorId(ctx.getVisitorId());
        row.setSessionId(normalizeUuid(ctx.getSessionId()));
        row.setUserId(ctx.getUserId());
        row.setEventType(type);
        row.setOrigin(LitemallUserEvent.ORIGIN_CLIENT);
        row.setOccurredAt(clampOccurredAt(dto.getOccurredAt(), receivedAt));
        row.setReceivedAt(receivedAt);
        row.setGoodsId(positiveOrNull(dto.getGoodsId()));
        row.setProductId(positiveOrNull(dto.getProductId()));
        row.setCategoryId(positiveOrNull(dto.getCategoryId()));
        row.setSearchQuery(truncate(dto.getSearchQuery(), 255));
        Integer position = positiveOrNull(dto.getPosition());
        row.setPosition(position != null && position <= Short.MAX_VALUE ? position : null);
        row.setPageType(truncate(dto.getPageType(), 32));
        row.setLocale(ctx.getLocale());
        row.setCountryCode(ctx.getCountryCode());
        row.setDeviceType(ctx.getDeviceType());
        row.setPayload(serializePayload(dto));
        return row;
    }

    private boolean hasRequiredFields(TrackEventDto dto, String type) {
        return switch (type) {
            case LitemallUserEvent.TYPE_VIEW_ITEM,
                 LitemallUserEvent.TYPE_CLICK_RESULT,
                 LitemallUserEvent.TYPE_ADD_TO_CART,
                 LitemallUserEvent.TYPE_REMOVE_FROM_CART -> positiveOrNull(dto.getGoodsId()) != null;
            case LitemallUserEvent.TYPE_VIEW_CATEGORY -> positiveOrNull(dto.getCategoryId()) != null;
            case LitemallUserEvent.TYPE_SEARCH -> dto.getSearchQuery() != null && !dto.getSearchQuery().isBlank();
            case LitemallUserEvent.TYPE_PAGE_VIEW -> dto.getPageType() != null && !dto.getPageType().isBlank();
            default -> true;
        };
    }

    private String serializePayload(TrackEventDto dto) {
        if (dto.getPayload() == null || dto.getPayload().isEmpty()) {
            return null;
        }
        try {
            String json = PAYLOAD_JSON.writeValueAsString(dto.getPayload());
            return json.length() <= PAYLOAD_MAX_CHARS ? json : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Client clocks are wrong, sometimes by hours: order within a session by
     * occurred_at, but clamp it into a sane window around received_at, which is
     * the audit anchor.
     */
    private LocalDateTime clampOccurredAt(Long occurredAtMillis, LocalDateTime receivedAt) {
        if (occurredAtMillis == null) {
            return receivedAt;
        }
        long receivedMillis = receivedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long clamped = Math.max(receivedMillis - CLOCK_PAST_TOLERANCE_MS,
                Math.min(receivedMillis + CLOCK_FUTURE_TOLERANCE_MS, occurredAtMillis));
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(clamped), ZoneId.systemDefault());
    }

    public static String normalizeUuid(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return UUID_36.matcher(trimmed).matches() ? trimmed.toLowerCase() : null;
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
