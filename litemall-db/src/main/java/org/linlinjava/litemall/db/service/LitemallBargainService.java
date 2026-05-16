package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallBargainMapper;
import org.linlinjava.litemall.db.domain.LitemallBargain;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallBargainService {

    @Resource
    private LitemallBargainMapper bargainMapper;

    public void add(LitemallBargain bargain) {
        bargain.setAddTime(LocalDateTime.now());
        bargain.setUpdateTime(LocalDateTime.now());
        bargain.setDeleted(false);
        bargainMapper.insertSelective(bargain);
    }

    /**
     * Return all currently active bargain campaigns (status=1, within time window, not deleted).
     */
    public List<LitemallBargain> queryActive() {
        return bargainMapper.selectActive();
    }

    public LitemallBargain findById(Integer id) {
        return bargainMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallBargain bargain) {
        return bargainMapper.updateByPrimaryKeySelective(bargain);
    }

    public int deleteById(Integer id) {
        return bargainMapper.logicalDeleteByPrimaryKey(id);
    }
}
