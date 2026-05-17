package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;

import java.util.List;
import java.util.Optional;

public interface LitemallBargainRepository {

    Optional<LitemallBargainAggregate> findById(LitemallBargainId bargainId);

    List<LitemallBargainAggregate> findAllActive();

    void save(LitemallBargainAggregate bargain);
}
