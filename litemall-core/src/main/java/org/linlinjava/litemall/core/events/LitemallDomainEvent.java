package org.linlinjava.litemall.core.events;

import java.time.LocalDateTime;

public abstract class LitemallDomainEvent {

    private final LocalDateTime occurredOn;

    public LitemallDomainEvent() {
        this.occurredOn = LocalDateTime.now();
    }

    public LocalDateTime occuredOn() {
        return occurredOn;
    }
}
