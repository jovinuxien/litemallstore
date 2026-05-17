package org.linlinjava.litemall.order.domain.events.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;

public class LitemallGrouponSucceededEvent extends LitemallDomainEvent {

    private final LitemallGrouponId grouponId;

    public LitemallGrouponSucceededEvent(LitemallGrouponId grouponId) {
        this.grouponId = grouponId;
    }


}
