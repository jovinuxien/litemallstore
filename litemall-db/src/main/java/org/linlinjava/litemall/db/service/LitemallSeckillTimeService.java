package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallSeckillTimeMapper;
import org.linlinjava.litemall.db.domain.LitemallSeckillTime;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallSeckillTimeService {

    @Resource
    private LitemallSeckillTimeMapper seckillTimeMapper;

    public void add(LitemallSeckillTime seckillTime) {
        seckillTime.setAddTime(LocalDateTime.now());
        seckillTime.setUpdateTime(LocalDateTime.now());
        seckillTime.setDeleted(false);
        seckillTimeMapper.insertSelective(seckillTime);
    }

    /**
     * Return all enabled (status=1) seckill time slots, ordered by hour.
     */
    public List<LitemallSeckillTime> queryEnabled() {
        return seckillTimeMapper.selectEnabled();
    }

    /**
     * Find the time slot record for a specific hour (0-23).
     */
    public LitemallSeckillTime findByHour(Byte hour) {
        return seckillTimeMapper.selectByHour(hour);
    }

    public LitemallSeckillTime findById(Integer id) {
        return seckillTimeMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallSeckillTime seckillTime) {
        return seckillTimeMapper.updateByPrimaryKeySelective(seckillTime);
    }

    public int deleteById(Integer id) {
        return seckillTimeMapper.logicalDeleteByPrimaryKey(id);
    }
}
