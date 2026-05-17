package org.linlinjava.litemall.wallet.domain.model.repositories;

import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallBillId;

import java.util.List;

public interface LitemallBillRepository {

    void add(LitemallBillAggregate bill);

    List<LitemallBillAggregate> findByUserId(LitemallUserId userId);

    LitemallBillAggregate findById(LitemallBillId billId);
}
