package org.linlinjava.litemall.loyalty.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserIntegralRecordMapper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUserIntegralRecord;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallLoyaltyPointsAggregate;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallPointsRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallPointsId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallPointsRepositoryImpl implements LitemallPointsRepository {

    private final LitemallUserIntegralRecordMapper integralRecordMapper;
    private final LitemallUserMapper userMapper;

    public LitemallPointsRepositoryImpl(LitemallUserIntegralRecordMapper integralRecordMapper,
                                        LitemallUserMapper userMapper) {
        this.integralRecordMapper = integralRecordMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Integer getBalance(LitemallUserId userId) {
        Integer balance = userMapper.selectIntegralByUserId(userId.getId());
        return balance != null ? balance : 0;
    }

    @Override
    public void addRecord(LitemallPointsId pointsId, LitemallUserId userId, int change, int balance,
                          String title, String linkId, String linkType, String mark) {
        LitemallUserIntegralRecord record = new LitemallUserIntegralRecord();
        record.setUserId(userId.getId());
        record.setLinkId(linkId);
        record.setLinkType(linkType);
        record.setTitle(title);
        record.setNumber(change);
        record.setBalance(balance);
        record.setMark(mark);
        record.setStatus((byte) 1);
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        integralRecordMapper.insertSelective(record);
    }

    @Override
    public List<LitemallLoyaltyPointsAggregate> findRecordsByUserId(LitemallUserId userId) {
        return integralRecordMapper.selectByUserId(userId.getId()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int sumPositiveByUserId(LitemallUserId userId) {
        Integer sum = integralRecordMapper.sumNumberByUserId(userId.getId());
        return sum != null ? sum : 0;
    }

    @Override
    public void updateUserBalance(LitemallUserId userId, int newBalance) {
        if (newBalance > 0) {
            int current = getBalance(userId);
            int delta = newBalance - current;
            if (delta > 0) {
                userMapper.incrementIntegral(userId.getId(), delta);
            } else if (delta < 0) {
                userMapper.decrementIntegral(userId.getId(), -delta);
            }
        }
    }

    private LitemallLoyaltyPointsAggregate toDomain(LitemallUserIntegralRecord r) {
        LitemallLoyaltyPointsAggregate agg = new LitemallLoyaltyPointsAggregate();
        agg.setPointsId(new LitemallPointsId(r.getId()));
        agg.setUserId(new LitemallUserId(r.getUserId()));
        agg.setChange(r.getNumber());
        agg.setBalance(r.getBalance());
        agg.setTitle(r.getTitle());
        agg.setLinkId(r.getLinkId());
        agg.setLinkType(r.getLinkType());
        agg.setMark(r.getMark());
        agg.setAddTime(r.getAddTime());
        agg.setUpdateTime(r.getUpdateTime());
        return agg;
    }
}