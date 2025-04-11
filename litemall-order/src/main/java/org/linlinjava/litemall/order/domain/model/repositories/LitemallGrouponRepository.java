package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;

public interface LitemallGrouponRepository {

    LitemallGrouponId nextIdentity(LitemallGrouponId id);

    void saveGroupon(LitemallGrouponAggregate grouponAggregate);
    int countGroupon(LitemallGrouponId grouponId);
    int countByGrouponId(LitemallGrouponId id);

    boolean existsByUserIdOrGrouponId(LitemallUserId userId, LitemallGrouponId grouponId);

    LitemallGroupon findById(LitemallGrouponId id);
    LitemallGroupon findByUserId(LitemallGrouponId id, LitemallUserId userId);
    LitemallGroupon getMyGroupon(LitemallUserId userId);

    List<LitemallGroupon> getJoinRecord(LitemallGrouponId grouponId);
    LitemallGroupon getMyJoinGroupon(LitemallUserId userId);
    LitemallGroupon getGrouponByOrderId(LitemallOrderId orderId);
}
