package org.linlinjava.litemall.order.domain.events.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.AllArgsConstructor;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;

@AllArgsConstructor
public class LitemallGrouponCreatedEvent extends LitemallDomainEvent {

    private final LitemallGrouponAggregate grouponAggregate;

    /*public LitemallGrouponCreatedEvent(LitemallGrouponAggregate grouponAggregate) {
        super("GROUPON_CREATED");
        this.grouponAggregate = grouponAggregate;
    }*/

    public LitemallGrouponAggregate getGroupon() {
        return grouponAggregate;
    }
}
