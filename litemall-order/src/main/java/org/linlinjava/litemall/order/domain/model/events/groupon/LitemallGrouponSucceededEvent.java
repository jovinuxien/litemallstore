package org.linlinjava.litemall.order.domain.model.events.groupon;

import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;

public class LitemallGrouponSucceededEvent extends LitemallDomainEvent {

    private final LitemallGrouponId grouponId;

    public LitemallGrouponSucceededEvent(LitemallGrouponId grouponId) {
        this.grouponId = grouponId;
    }


}
