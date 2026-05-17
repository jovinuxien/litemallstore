package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserIntegralRecordMapper;
import org.linlinjava.litemall.db.domain.LitemallUserIntegralRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserIntegralRecordService {

    @Resource
    private LitemallUserIntegralRecordMapper integralRecordMapper;

    public void add(LitemallUserIntegralRecord record) {
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        integralRecordMapper.insertSelective(record);
    }

    public List<LitemallUserIntegralRecord> queryByUserId(Integer userId) {
        return integralRecordMapper.selectByUserId(userId);
    }

    public LitemallUserIntegralRecord findById(Integer id) {
        return integralRecordMapper.selectByPrimaryKey(id);
    }

    /**
     * Sum all valid integral changes for a user (positive = earned, negative = spent).
     * Returns the net integral balance.
     */
    public Integer sumByUserId(Integer userId) {
        Integer sum = integralRecordMapper.sumNumberByUserId(userId);
        return sum == null ? 0 : sum;
    }

    public int updateById(LitemallUserIntegralRecord record) {
        return integralRecordMapper.updateByPrimaryKeySelective(record);
    }

    public int deleteById(Integer id) {
        return integralRecordMapper.logicalDeleteByPrimaryKey(id);
    }
}
