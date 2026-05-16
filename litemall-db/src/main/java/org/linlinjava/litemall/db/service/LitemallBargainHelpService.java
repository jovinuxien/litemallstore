package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallBargainHelpMapper;
import org.linlinjava.litemall.db.domain.LitemallBargainHelp;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallBargainHelpService {

    @Resource
    private LitemallBargainHelpMapper bargainHelpMapper;

    public void add(LitemallBargainHelp bargainHelp) {
        bargainHelp.setAddTime(LocalDateTime.now());
        bargainHelp.setUpdateTime(LocalDateTime.now());
        bargainHelp.setDeleted(false);
        bargainHelpMapper.insertSelective(bargainHelp);
    }

    /**
     * Count how many helpers have assisted a specific user's bargain record.
     */
    public int countByBargainUser(Integer bargainUserId) {
        return bargainHelpMapper.countByBargainUserId(bargainUserId);
    }

    /**
     * Return all help records for a specific user's bargain participation.
     */
    public List<LitemallBargainHelp> findByBargainUser(Integer bargainUserId) {
        return bargainHelpMapper.selectByBargainUserId(bargainUserId);
    }

    public LitemallBargainHelp findById(Integer id) {
        return bargainHelpMapper.selectByPrimaryKey(id);
    }

    public List<LitemallBargainHelp> queryByUserId(Integer userId) {
        return bargainHelpMapper.selectByUserId(userId);
    }

    public int updateById(LitemallBargainHelp bargainHelp) {
        return bargainHelpMapper.updateByPrimaryKeySelective(bargainHelp);
    }

    public int deleteById(Integer id) {
        return bargainHelpMapper.logicalDeleteByPrimaryKey(id);
    }
}
