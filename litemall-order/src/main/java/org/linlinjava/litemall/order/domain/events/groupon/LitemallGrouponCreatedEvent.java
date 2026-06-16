package org.linlinjava.litemall.order.domain.events.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;

@Getter
public class LitemallGrouponCreatedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallGrouponAggregate grouponAggregate;

    public LitemallGrouponCreatedEvent(LitemallGrouponAggregate grouponAggregate) {
        super(SCHEMA_VERSION);
        this.grouponAggregate = grouponAggregate;
    }

    public LitemallGrouponAggregate getGroupon() {
        return grouponAggregate;
    }
}
