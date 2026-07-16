package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallSeckillService {

    @Resource
    private LitemallSeckillMapper seckillMapper;

    public void add(LitemallSeckill seckill) {
        seckill.setAddTime(LocalDateTime.now());
        seckill.setUpdateTime(LocalDateTime.now());
        seckill.setDeleted(false);
        seckillMapper.insertSelective(seckill);
    }

    /**
     * Return all currently active seckill items (status=1, within time window, not deleted).
     */
    public List<LitemallSeckill> queryActive() {
        return seckillMapper.selectActive();
    }

    public LitemallSeckill findById(Integer id) {
        return seckillMapper.selectByPrimaryKey(id);
    }

    /**
     * Atomically decrease stock and increase sales by the given quantity.
     * Returns the number of rows affected (0 means insufficient stock).
     */
    public int updateStock(Integer id, Integer quantity) {
        return seckillMapper.decreaseStock(id, quantity);
    }

    public int updateById(LitemallSeckill seckill) {
        return seckillMapper.updateByPrimaryKeySelective(seckill);
    }

    public int deleteById(Integer id) {
        return seckillMapper.logicalDeleteByPrimaryKey(id);
    }

    // --- V38 flash-deal lifecycle (goods-management price-swap scheduler) ---

    public List<LitemallSeckill> queryDueForActivation() {
        return seckillMapper.selectDueForActivation();
    }

    public List<LitemallSeckill> queryDueForExpiry() {
        return seckillMapper.selectDueForExpiry();
    }

    public List<LitemallSeckill> querySwapped() {
        return seckillMapper.selectSwapped();
    }

    public LitemallSeckill findLiveByGoodsId(Integer goodsId) {
        return seckillMapper.selectLiveByGoodsId(goodsId);
    }

    public boolean hasOverlapping(Integer goodsId, LocalDateTime start, LocalDateTime stop, Integer excludeId) {
        return seckillMapper.countOverlapping(goodsId, start, stop, excludeId == null ? 0 : excludeId) > 0;
    }

    public List<LitemallSeckill> queryAdminPage(int offset, int limit) {
        return seckillMapper.selectAdminPage(offset, limit);
    }

    public int countAdmin() {
        return seckillMapper.countAdmin();
    }

    public int sumPaidQuantityInWindow(Integer goodsId, LocalDateTime start, LocalDateTime stop) {
        return seckillMapper.sumPaidQuantityInWindow(goodsId, start, stop);
    }
}
