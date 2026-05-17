package org.linlinjava.litemall.loyalty.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserExperienceRecordMapper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUserExperienceRecord;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallExperienceRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallExperienceId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class LitemallExperienceRepositoryImpl implements LitemallExperienceRepository {

    private final LitemallUserExperienceRecordMapper experienceRecordMapper;
    private final LitemallUserMapper userMapper;

    public LitemallExperienceRepositoryImpl(LitemallUserExperienceRecordMapper experienceRecordMapper,
                                            LitemallUserMapper userMapper) {
        this.experienceRecordMapper = experienceRecordMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Integer getBalance(LitemallUserId userId) {
        Integer balance = userMapper.selectExperienceByUserId(userId.getId());
        return balance != null ? balance : 0;
    }

    @Override
    public void addRecord(LitemallExperienceId experienceId, LitemallUserId userId, int change, int balance,
                          String title, String linkId, String linkType) {
        LitemallUserExperienceRecord record = new LitemallUserExperienceRecord();
        record.setUserId(userId.getId());
        record.setLinkId(linkId);
        record.setLinkType(linkType);
        record.setTitle(title);
        record.setExperience(change);
        record.setBalance(balance);
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        experienceRecordMapper.insertSelective(record);
    }

    @Override
    public void updateUserExperience(LitemallUserId userId, int newBalance) {
        int current = getBalance(userId);
        int delta = newBalance - current;
        if (delta > 0) {
            userMapper.incrementExperience(userId.getId(), delta);
        }
    }
}