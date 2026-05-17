package org.linlinjava.litemall.wallet.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.repositories.LitemallWalletRepository;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallWalletId;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class LitemallWalletRepositoryImpl implements LitemallWalletRepository {

    private final LitemallUserMapper userMapper;

    public LitemallWalletRepositoryImpl(LitemallUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<LitemallWalletAggregate> findByUserId(LitemallUserId userId) {
        LitemallUser user = userMapper.selectByPrimaryKey(userId.getId());
        if (user == null) return Optional.empty();
        return Optional.of(toAggregate(user));
    }

    @Override
    public void creditBalance(LitemallUserId userId, LitemallMoney amount) {
        int updated = userMapper.incrementNowMoney(userId.getId(), amount.getAmount());
        if (updated == 0) throw new IllegalStateException("User not found for credit: " + userId.getId());
    }

    @Override
    public void debitBalance(LitemallUserId userId, LitemallMoney amount) {
        int updated = userMapper.decrementNowMoney(userId.getId(), amount.getAmount());
        if (updated == 0) throw new IllegalStateException("Insufficient balance or user not found: " + userId.getId());
    }

    @Override
    public LitemallMoney getBalance(LitemallUserId userId) {
        BigDecimal balance = userMapper.selectNowMoneyByUserId(userId.getId());
        return new LitemallMoney(balance != null ? balance : BigDecimal.ZERO);
    }

    private LitemallWalletAggregate toAggregate(LitemallUser user) {
        BigDecimal nowMoney = user.getNowMoney() != null ? user.getNowMoney() : BigDecimal.ZERO;
        BigDecimal brokerage = user.getBrokeragePrice() != null ? user.getBrokeragePrice() : BigDecimal.ZERO;
        return new LitemallWalletAggregate(
                new LitemallWalletId(user.getId()),
                new LitemallUserId(user.getId()),
                new LitemallMoney(nowMoney),
                new LitemallMoney(brokerage)
        );
    }
}
