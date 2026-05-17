package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainUserAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallBargainUserStatus;

import java.util.Optional;

public interface LitemallBargainUserRepository {

    void add(LitemallBargainUserAggregate bargainUser);

    Optional<LitemallBargainUserAggregate> findById(LitemallBargainUserId id);

    Optional<LitemallBargainUserAggregate> findByUserAndBargain(LitemallUserId userId, LitemallBargainId bargainId);

    int updateStatus(LitemallBargainUserId id, LitemallBargainUserStatus status);

    int updateBargainPrice(LitemallBargainUserId id, LitemallMoney newPrice);
}
