package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserBillMapper;
import org.linlinjava.litemall.db.domain.LitemallUserBill;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserBillService {

    @Resource
    private LitemallUserBillMapper billMapper;

    public void add(LitemallUserBill bill) {
        bill.setAddTime(LocalDateTime.now());
        bill.setUpdateTime(LocalDateTime.now());
        bill.setDeleted(false);
        billMapper.insertSelective(bill);
    }

    public List<LitemallUserBill> queryByUserId(Integer userId) {
        return billMapper.selectByUserId(userId);
    }

    public LitemallUserBill findById(Integer id) {
        return billMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallUserBill bill) {
        return billMapper.updateByPrimaryKeySelective(bill);
    }

    public int deleteById(Integer id) {
        return billMapper.logicalDeleteByPrimaryKey(id);
    }
}
