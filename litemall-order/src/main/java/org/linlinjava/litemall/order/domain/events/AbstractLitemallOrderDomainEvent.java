package org.linlinjava.litemall.order.domain.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

// Order-scoped base over litemall-core's LitemallDomainEvent.
//
// core's LitemallDomainEvent now carries correlationId, schemaVersion and
// occurredOn itself, so this base no longer redeclares them (doing so shadowed
// the core fields and made Jackson see duplicate correlationId/schemaVersion
// properties, and clashed core's String getSchemaVersion() with an int one).
// It now only adapts the per-event int SCHEMA_VERSION constants to core's
// String schemaVersion and keeps field-level Jackson visibility so subclass
// payload fields round-trip through the Spring Cloud Stream JSON converter.
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class AbstractLitemallOrderDomainEvent extends LitemallDomainEvent {

    protected AbstractLitemallOrderDomainEvent() {
        super();
    }

    protected AbstractLitemallOrderDomainEvent(int schemaVersion) {
        super(null, String.valueOf(schemaVersion));
    }
}
