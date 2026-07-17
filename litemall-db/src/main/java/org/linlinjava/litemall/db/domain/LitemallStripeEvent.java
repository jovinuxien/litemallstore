package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One processed Stripe webhook event (table {@code litemall_stripe_event}, created in
 * V43 — Wave 7 payment verification).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallMailOutbox}.
 *
 * <p>This is an idempotency ledger, not business data: Stripe retries deliveries, so the
 * same {@code eventId} can arrive repeatedly. A row's existence means "already handled".
 * The UNIQUE key on {@code event_id} — not a read-then-write check — is what makes that
 * claim race-free, so rows are never soft-deleted: a purged row would let an old event
 * replay against a live order.
 */
public class LitemallStripeEvent {

    public static final String TYPE_PAYMENT_SUCCEEDED = "payment_intent.succeeded";
    public static final String TYPE_PAYMENT_FAILED = "payment_intent.payment_failed";

    private Integer id;
    /** Stripe event id (evt_...) — the idempotency key. */
    private String eventId;
    /** e.g. payment_intent.succeeded | payment_intent.payment_failed. */
    private String eventType;
    /** Resolved litemall_order.id, when the event mapped to one. */
    private Integer orderId;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
