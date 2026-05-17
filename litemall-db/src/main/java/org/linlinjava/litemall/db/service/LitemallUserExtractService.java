package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallUserExtractMapper;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallUserExtractService {

    @Resource
    private LitemallUserExtractMapper extractMapper;

    public void add(LitemallUserExtract extract) {
        extract.setAddTime(LocalDateTime.now());
        extract.setUpdateTime(LocalDateTime.now());
        extract.setDeleted(false);
        extractMapper.insertSelective(extract);
    }

    public List<LitemallUserExtract> queryByUserId(Integer userId) {
        return extractMapper.selectByUserId(userId);
    }

    public LitemallUserExtract findById(Integer id) {
        return extractMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallUserExtract extract) {
        return extractMapper.updateByPrimaryKeySelective(extract);
    }

    public int deleteById(Integer id) {
        return extractMapper.logicalDeleteByPrimaryKey(id);
    }
}
