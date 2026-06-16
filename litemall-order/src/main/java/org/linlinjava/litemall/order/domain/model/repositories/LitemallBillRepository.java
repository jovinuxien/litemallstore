package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallBillId;

import java.util.List;

public interface LitemallBillRepository {

    void add(LitemallBillAggregate bill);

    List<LitemallBillAggregate> findByUserId(LitemallUserId userId);

    LitemallBillAggregate findById(LitemallBillId billId);
}
