package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainHelpAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;

import java.util.List;

public interface LitemallBargainHelpRepository {

    void add(LitemallBargainHelpAggregate help);

    int countByBargainUserId(LitemallBargainUserId bargainUserId);

    List<LitemallBargainHelpAggregate> findByBargainUserId(LitemallBargainUserId bargainUserId);
}
