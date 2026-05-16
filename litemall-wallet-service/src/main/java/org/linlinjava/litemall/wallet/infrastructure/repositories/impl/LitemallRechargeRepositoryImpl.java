package org.linlinjava.litemall.wallet.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserRechargeMapper;
import org.linlinjava.litemall.db.domain.LitemallUserRecharge;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallRechargeAggregate;
import org.linlinjava.litemall.wallet.domain.model.repositories.LitemallRechargeRepository;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.enums.LitemallRechargeType;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallRechargeId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallRechargeRepositoryImpl implements LitemallRechargeRepository {

    private final LitemallUserRechargeMapper rechargeMapper;

    public LitemallRechargeRepositoryImpl(LitemallUserRechargeMapper rechargeMapper) {
        this.rechargeMapper = rechargeMapper;
    }

    @Override
    public void add(LitemallRechargeAggregate recharge) {
        LitemallUserRecharge record = toDataModel(recharge);
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        record.setPaid(false);
        rechargeMapper.insertSelective(record);
        recharge.setRechargeId(new LitemallRechargeId(record.getId()));
    }

    @Override
    public Optional<LitemallRechargeAggregate> findById(LitemallRechargeId rechargeId) {
        LitemallUserRecharge record = rechargeMapper.selectByPrimaryKey(rechargeId.getId());
        return record != null ? Optional.of(toDomainModel(record)) : Optional.empty();
    }

    @Override
    public List<LitemallRechargeAggregate> findByUserId(LitemallUserId userId) {
        return rechargeMapper.selectByUserId(userId.getId()).stream()
                .map(this::toDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public void markAsPaid(LitemallRechargeId rechargeId) {
        LitemallUserRecharge record = new LitemallUserRecharge();
        record.setId(rechargeId.getId());
        record.setPaid(true);
        record.setPayTime(LocalDateTime.now());
        rechargeMapper.updateByPrimaryKeySelective(record);
    }

    private LitemallRechargeAggregate toDomainModel(LitemallUserRecharge r) {
        LitemallRechargeAggregate agg = new LitemallRechargeAggregate();
        agg.setRechargeId(new LitemallRechargeId(r.getId()));
        agg.setUserId(new LitemallUserId(r.getUserId()));
        agg.setOrderId(r.getOrderId());
        agg.setPrice(new LitemallMoney(r.getPrice()));
        agg.setGivePrice(r.getGivePrice() != null ? new LitemallMoney(r.getGivePrice()) : new LitemallMoney(java.math.BigDecimal.ZERO));
        agg.setRechargeType(r.getRechargeType() != null ? LitemallRechargeType.fromValue(r.getRechargeType()) : null);
        agg.setPaid(r.getPaid());
        agg.setPayTime(r.getPayTime());
        agg.setAddTime(r.getAddTime());
        agg.setUpdateTime(r.getUpdateTime());
        return agg;
    }

    private LitemallUserRecharge toDataModel(LitemallRechargeAggregate agg) {
        LitemallUserRecharge r = new LitemallUserRecharge();
        if (agg.getRechargeId() != null) r.setId(agg.getRechargeId().getId());
        r.setUserId(agg.getUserId().getId());
        r.setOrderId(agg.getOrderId());
        r.setPrice(agg.getPrice().getAmount());
        r.setGivePrice(agg.getGivePrice() != null ? agg.getGivePrice().getAmount() : java.math.BigDecimal.ZERO);
        r.setRechargeType(agg.getRechargeType() != null ? agg.getRechargeType().getValue() : null);
        r.setPaid(agg.getPaid());
        r.setPayTime(agg.getPayTime());
        return r;
    }
}
