package org.linlinjava.litemall.order.domain.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

import java.time.LocalDateTime;

// Order-scoped base. Adds cross-process transport fields (correlationId set by
// the publisher; schemaVersion per subclass) on top of litemall-core's
// LitemallDomainEvent. Kept in litemall-order to avoid rippling into other
// modules that extend the core base (wallet, loyalty, promotion).
@Getter
@Setter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class AbstractLitemallOrderDomainEvent extends LitemallDomainEvent {

    private String correlationId;
    private int schemaVersion;
    private LocalDateTime occurredAt;

    protected AbstractLitemallOrderDomainEvent() {
        super();
        this.occurredAt = LocalDateTime.now();
    }

    protected AbstractLitemallOrderDomainEvent(int schemaVersion) {
        super();
        this.schemaVersion = schemaVersion;
        this.occurredAt = LocalDateTime.now();
    }
}
