package org.linlinjava.litemall.order.domain.events.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;

@Getter
public class LitemallGrouponSucceededEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallGrouponId grouponId;

    public LitemallGrouponSucceededEvent(LitemallGrouponId grouponId) {
        super(SCHEMA_VERSION);
        this.grouponId = grouponId;
    }
}
