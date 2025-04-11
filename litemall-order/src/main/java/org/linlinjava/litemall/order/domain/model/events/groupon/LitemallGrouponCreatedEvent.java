package org.linlinjava.litemall.order.domain.model.events.groupon;

import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;

public class LitemallGrouponCreatedEvent extends LitemallDomainEvent {

    private final LitemallGroupon groupon;

    public LitemallGrouponCreatedEvent(LitemallGroupon groupon) {
        this.groupon = groupon;
    }

    public LitemallGroupon getGroupon() {
        return groupon;
    }
}
