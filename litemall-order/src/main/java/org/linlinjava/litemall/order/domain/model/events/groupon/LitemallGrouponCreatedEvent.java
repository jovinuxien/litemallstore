package org.linlinjava.litemall.order.domain.model.events.groupon;

import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;

public class LitemallGrouponCreatedEvent extends LitemallDomainEvent {

    private final LitemallGrouponAggregate grouponAggregate;

    public LitemallGrouponCreatedEvent(LitemallGrouponAggregate grouponAggregate) {
        this.grouponAggregate = grouponAggregate;
    }

    public LitemallGrouponAggregate getGroupon() {
        return grouponAggregate;
    }
}
