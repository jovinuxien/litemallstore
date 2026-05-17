package org.linlinjava.litemall.core.events;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public abstract class LitemallDomainEvent {


    private  final String eventName;
    private final LocalDateTime occurredOn;

    public LitemallDomainEvent(String eventName) {
        this.occurredOn = LocalDateTime.now();
        this.eventName = eventName;
    }
    protected LitemallDomainEvent() {
        this.occurredOn = LocalDateTime.now();
        this.eventName = null; // or some default
    }

    public LocalDateTime occuredOn() {
        return occurredOn;
    }

}
