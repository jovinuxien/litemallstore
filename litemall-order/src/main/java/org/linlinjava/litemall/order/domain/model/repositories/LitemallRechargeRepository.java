package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallRechargeAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallRechargeId;

import java.util.List;
import java.util.Optional;

public interface LitemallRechargeRepository {

    void add(LitemallRechargeAggregate recharge);

    Optional<LitemallRechargeAggregate> findById(LitemallRechargeId rechargeId);

    List<LitemallRechargeAggregate> findByUserId(LitemallUserId userId);

    void markAsPaid(LitemallRechargeId rechargeId);
}
