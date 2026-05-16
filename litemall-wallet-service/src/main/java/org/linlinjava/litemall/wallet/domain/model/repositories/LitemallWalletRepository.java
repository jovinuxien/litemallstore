package org.linlinjava.litemall.wallet.domain.model.repositories;

import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;

import java.util.Optional;

public interface LitemallWalletRepository {

    Optional<LitemallWalletAggregate> findByUserId(LitemallUserId userId);

    void creditBalance(LitemallUserId userId, LitemallMoney amount);

    void debitBalance(LitemallUserId userId, LitemallMoney amount);

    LitemallMoney getBalance(LitemallUserId userId);
}
