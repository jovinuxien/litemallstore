package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallBargainUserMapper;
import org.linlinjava.litemall.db.domain.LitemallBargainUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallBargainUserService {

    @Resource
    private LitemallBargainUserMapper bargainUserMapper;

    public void add(LitemallBargainUser bargainUser) {
        bargainUser.setAddTime(LocalDateTime.now());
        bargainUser.setUpdateTime(LocalDateTime.now());
        bargainUser.setDeleted(false);
        bargainUserMapper.insertSelective(bargainUser);
    }

    /**
     * Find the most recent bargain participation record for a user and campaign.
     */
    public LitemallBargainUser findByUserAndBargain(Integer userId, Integer bargainId) {
        return bargainUserMapper.selectByUserIdAndBargainId(userId, bargainId);
    }

    public LitemallBargainUser findById(Integer id) {
        return bargainUserMapper.selectByPrimaryKey(id);
    }

    public List<LitemallBargainUser> queryByUserId(Integer userId) {
        return bargainUserMapper.selectByUserId(userId);
    }

    public int update(LitemallBargainUser bargainUser) {
        return bargainUserMapper.updateByPrimaryKeySelective(bargainUser);
    }

    public int deleteById(Integer id) {
        return bargainUserMapper.logicalDeleteByPrimaryKey(id);
    }
}
