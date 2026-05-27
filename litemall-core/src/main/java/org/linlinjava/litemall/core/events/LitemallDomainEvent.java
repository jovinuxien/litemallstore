package org.linlinjava.litemall.core.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base type for all litemall domain events. Carries a correlationId (for
 * cross-process tracing) and a schemaVersion (so consumers can route old
 * payloads through compatibility shims). Both fields are populated at
 * construction; correlationId can be overridden via {@link #setCorrelationId}
 * if a publisher wants to bind it to a specific request context (e.g. from
 * {@code UserContext.getCorrelationId()}).
 *
 * <p>Jackson visibility is set so Lombok-generated getters round-trip via the
 * Spring Cloud Stream JSON message converter without per-event annotations.
 */
@Getter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public abstract class LitemallDomainEvent {

    private static final String DEFAULT_SCHEMA_VERSION = "1";

    private final String eventName;
    private final LocalDateTime occurredOn;
    private final String schemaVersion;

    @Setter
    private String correlationId;

    public LitemallDomainEvent(String eventName) {
        this(eventName, DEFAULT_SCHEMA_VERSION);
    }

    public LitemallDomainEvent(String eventName, String schemaVersion) {
        this.occurredOn = LocalDateTime.now();
        this.eventName = eventName;
        this.schemaVersion = schemaVersion;
        this.correlationId = UUID.randomUUID().toString();
    }

    protected LitemallDomainEvent() {
        this(null, DEFAULT_SCHEMA_VERSION);
    }

    public LocalDateTime occuredOn() {
        return occurredOn;
    }
}
