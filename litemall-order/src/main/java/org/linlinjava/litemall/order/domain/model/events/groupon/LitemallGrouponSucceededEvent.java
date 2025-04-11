package org.linlinjava.litemall.order.domain.model.events.groupon;

import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;

public class LitemallGrouponSucceededEvent extends LitemallDomainEvent {

    private final LitemallGroupon groupon;

    public LitemallGrouponSucceededEvent(LitemallGroupon groupon) {
        this.groupon = groupon;
    }


}
