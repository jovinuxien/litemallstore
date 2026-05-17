package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallSystemUserLevelMapper;
import org.linlinjava.litemall.db.domain.LitemallSystemUserLevel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallSystemUserLevelService {

    @Resource
    private LitemallSystemUserLevelMapper systemUserLevelMapper;

    public void add(LitemallSystemUserLevel systemUserLevel) {
        systemUserLevel.setAddTime(LocalDateTime.now());
        systemUserLevel.setUpdateTime(LocalDateTime.now());
        systemUserLevel.setDeleted(false);
        systemUserLevelMapper.insertSelective(systemUserLevel);
    }

    /**
     * Return all non-deleted level definitions ordered by level value ascending.
     */
    public List<LitemallSystemUserLevel> findAll() {
        return systemUserLevelMapper.selectAll();
    }

    /**
     * Find the level definition whose level value matches exactly.
     */
    public LitemallSystemUserLevel findByLevel(Byte level) {
        return systemUserLevelMapper.selectByLevel(level);
    }

    /**
     * Find the next level definition above the given level value.
     */
    public LitemallSystemUserLevel findNextLevel(Byte level) {
        return systemUserLevelMapper.selectNextLevel(level);
    }

    public LitemallSystemUserLevel findById(Integer id) {
        return systemUserLevelMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallSystemUserLevel systemUserLevel) {
        return systemUserLevelMapper.updateByPrimaryKeySelective(systemUserLevel);
    }

    public int deleteById(Integer id) {
        return systemUserLevelMapper.logicalDeleteByPrimaryKey(id);
    }
}
