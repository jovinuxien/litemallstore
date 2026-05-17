package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserLevelMapper;
import org.linlinjava.litemall.db.domain.LitemallUserLevel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserLevelService {

    @Resource
    private LitemallUserLevelMapper userLevelMapper;

    public void add(LitemallUserLevel userLevel) {
        userLevel.setAddTime(LocalDateTime.now());
        userLevel.setUpdateTime(LocalDateTime.now());
        userLevel.setDeleted(false);
        userLevelMapper.insertSelective(userLevel);
    }

    public List<LitemallUserLevel> queryByUserId(Integer userId) {
        return userLevelMapper.selectByUserId(userId);
    }

    /**
     * Return the user's current active level record (highest active grade).
     */
    public LitemallUserLevel findCurrentLevelByUserId(Integer userId) {
        return userLevelMapper.selectCurrentByUserId(userId);
    }

    public LitemallUserLevel findById(Integer id) {
        return userLevelMapper.selectByPrimaryKey(id);
    }

    /**
     * Deactivate the old level record and insert the new one.
     * Should be called inside a transaction by the caller.
     */
    public void upgradeLevel(Integer userId, Integer oldLevelRecordId, LitemallUserLevel newLevel) {
        if (oldLevelRecordId != null) {
            LitemallUserLevel old = new LitemallUserLevel();
            old.setId(oldLevelRecordId);
            old.setStatus((byte) 0);
            userLevelMapper.updateByPrimaryKeySelective(old);
        }
        add(newLevel);
    }

    public int updateById(LitemallUserLevel userLevel) {
        return userLevelMapper.updateByPrimaryKeySelective(userLevel);
    }

    public int deleteById(Integer id) {
        return userLevelMapper.logicalDeleteByPrimaryKey(id);
    }
}
