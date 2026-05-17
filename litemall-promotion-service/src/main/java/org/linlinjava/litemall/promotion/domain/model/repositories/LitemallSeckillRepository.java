package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSeckillAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;

import java.util.List;
import java.util.Optional;

public interface LitemallSeckillRepository {

    Optional<LitemallSeckillAggregate> findById(LitemallSeckillId seckillId);

    List<LitemallSeckillAggregate> findActiveByHour(Byte hour);

    List<LitemallSeckillAggregate> findAllActive();

    int updateStock(LitemallSeckillId seckillId, int stockDelta);

    void save(LitemallSeckillAggregate seckill);
}
