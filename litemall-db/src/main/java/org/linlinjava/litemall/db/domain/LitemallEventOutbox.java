package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One row of the transactional outbox (table {@code litemall_event_outbox}, V25).
 * Hand-written, co-located with the generated domains.
 */
public class LitemallEventOutbox {

    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    /** Stream binding to forward to; null = audit-only (no broker binding for this type). */
    private String binding;
    private String payload;
    /** PENDING / SENT / FAILED. */
    private String status;
    private Integer attempts;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getBinding() { return binding; }
    public void setBinding(String binding) { this.binding = binding; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}