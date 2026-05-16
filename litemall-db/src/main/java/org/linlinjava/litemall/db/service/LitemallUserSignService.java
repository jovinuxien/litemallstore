package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserSignMapper;
import org.linlinjava.litemall.db.domain.LitemallUserSign;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserSignService {

    @Resource
    private LitemallUserSignMapper signMapper;

    public void add(LitemallUserSign sign) {
        sign.setAddTime(LocalDateTime.now());
        sign.setUpdateTime(LocalDateTime.now());
        sign.setDeleted(false);
        signMapper.insertSelective(sign);
    }

    /**
     * Check whether the user has already signed in today.
     */
    public boolean hasTodaySigned(Integer userId) {
        return signMapper.countTodayByUserId(userId) > 0;
    }

    public List<LitemallUserSign> queryByUserId(Integer userId) {
        return signMapper.selectByUserId(userId);
    }

    public LitemallUserSign findById(Integer id) {
        return signMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallUserSign sign) {
        return signMapper.updateByPrimaryKeySelective(sign);
    }

    public int deleteById(Integer id) {
        return signMapper.logicalDeleteByPrimaryKey(id);
    }
}
