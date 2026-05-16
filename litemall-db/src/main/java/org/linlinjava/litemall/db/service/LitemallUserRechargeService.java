package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserRechargeMapper;
import org.linlinjava.litemall.db.domain.LitemallUserRecharge;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserRechargeService {

    @Resource
    private LitemallUserRechargeMapper rechargeMapper;

    public void add(LitemallUserRecharge recharge) {
        recharge.setAddTime(LocalDateTime.now());
        recharge.setUpdateTime(LocalDateTime.now());
        recharge.setDeleted(false);
        rechargeMapper.insertSelective(recharge);
    }

    public List<LitemallUserRecharge> queryByUserId(Integer userId) {
        return rechargeMapper.selectByUserId(userId);
    }

    public LitemallUserRecharge findById(Integer id) {
        return rechargeMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallUserRecharge recharge) {
        return rechargeMapper.updateByPrimaryKeySelective(recharge);
    }

    public int deleteById(Integer id) {
        return rechargeMapper.logicalDeleteByPrimaryKey(id);
    }
}
