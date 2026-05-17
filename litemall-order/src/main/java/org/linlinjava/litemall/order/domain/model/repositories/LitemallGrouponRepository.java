package org.linlinjava.litemall.order.domain.model.repositories;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;

public interface LitemallGrouponRepository {

    LitemallGrouponId nextIdentity(LitemallGrouponId id);

    void saveGroupon(LitemallGrouponAggregate grouponAggregate);
    int countGroupon(LitemallGrouponId grouponId);
    int countByGrouponId(LitemallGrouponId id);
    int updateById(LitemallGrouponAggregate grouponAggregate);

    boolean hasJoin(LitemallUserId userId, LitemallGrouponId grouponId);
    boolean isGrouponCreator(LitemallUserId userId, LitemallGrouponId grouponId);

    LitemallGrouponAggregate findById(LitemallGrouponId id);
    LitemallGrouponAggregate findByUserId(LitemallGrouponId id, LitemallUserId userId);
    List<LitemallGrouponAggregate> getMyGroupon(LitemallUserId userId);

    List<LitemallGrouponAggregate> getJoinRecord(LitemallGrouponId grouponId);
    List<LitemallGrouponAggregate> getMyJoinGroupon(LitemallUserId userId);
    LitemallGrouponAggregate getGrouponByOrderId(LitemallOrderId orderId);

    LitemallGroupon convertToDataModel(LitemallGrouponAggregate grouponAggregate);
    LitemallGrouponAggregate convertToAggregate(LitemallGroupon groupon);

    int countUserActiveParticipationsInRules(LitemallUserId userId, LitemallGrouponRulesId rulesId);





    List<LitemallGrouponAggregate> findActiveParticipationsByUserAndRules(LitemallUserId userId, LitemallGrouponRulesId rulesId);

}
