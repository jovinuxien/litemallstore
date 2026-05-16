package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserExperienceRecordMapper;
import org.linlinjava.litemall.db.domain.LitemallUserExperienceRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserExperienceRecordService {

    @Resource
    private LitemallUserExperienceRecordMapper experienceRecordMapper;

    public void add(LitemallUserExperienceRecord record) {
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        experienceRecordMapper.insertSelective(record);
    }

    public List<LitemallUserExperienceRecord> queryByUserId(Integer userId) {
        return experienceRecordMapper.selectByUserId(userId);
    }

    public LitemallUserExperienceRecord findById(Integer id) {
        return experienceRecordMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallUserExperienceRecord record) {
        return experienceRecordMapper.updateByPrimaryKeySelective(record);
    }

    public int deleteById(Integer id) {
        return experienceRecordMapper.logicalDeleteByPrimaryKey(id);
    }
}
