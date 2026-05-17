package org.linlinjava.litemall.order.domain.events;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;

public interface LitemallDomainEventPublisher {
    void publish(LitemallDomainEvent event);
}
