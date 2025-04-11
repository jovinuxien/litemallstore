package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;

import java.util.List;

public interface LitemallGrouponRulesRepository {


    LitemallGrouponRules findById(LitemallGrouponRulesId id);

    void createGrouponRules(LitemallGrouponRules grouponRules);

    LitemallGrouponRules findGrouponRulesByGoodsId(LitemallGrouponRulesId goodsId);


    int countByGoodsId(LitemallGrouponRulesId goodsId);


    List<LitemallGrouponRules> getGrouponByStatus(Short status);

    boolean isGrouponRulesExpired(LitemallGrouponRules grouponRules);
    List<LitemallGrouponRules> getAllGrouponList(Integer page, Integer limit, String sort, String order);

    List<LitemallGrouponRules> findAllGrouponRulesList(LitemallGrouponId goodsId, Integer page, Integer size, String sort, String order);

    void deleteGrouponRulesById(LitemallGrouponRulesId goodsId);

    void updateGrouponRules(LitemallGrouponRules grouponRules);

}
