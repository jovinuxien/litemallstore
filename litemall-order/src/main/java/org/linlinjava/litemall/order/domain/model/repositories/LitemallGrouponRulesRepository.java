package org.linlinjava.litemall.order.domain.model.repositories;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;

import java.util.List;

public interface LitemallGrouponRulesRepository {


    LitemallGrouponRulesAggregate findById(LitemallGrouponRulesId id);

    int createGrouponRules(LitemallGrouponRulesAggregate grouponRulesAggregate);

    int countByGoodsId(LitemallGrouponRulesId goodsId);
    void deleteGrouponRulesById(LitemallGrouponRulesId goodsId);

    void updateGrouponRules(LitemallGrouponRulesAggregate grouponRulesAggregate);

    LitemallGrouponRulesAggregate findGrouponRulesByGoodsId(LitemallGrouponRulesId goodsId);
    List<LitemallGrouponRulesAggregate> getAllGrouponList(Integer page, Integer limit, String sort, String order);
    List<LitemallGrouponRulesAggregate> findAllGrouponRulesList(LitemallGoodsId goodsId, LitemallGrouponStatus status, Integer page, Integer size, String sort, String order);
    List<LitemallGrouponRulesAggregate> getGrouponByStatus(LitemallGrouponStatus status);

    Boolean isExpired(LitemallGrouponRulesAggregate rulesAggregate);


}
