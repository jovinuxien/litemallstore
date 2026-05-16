package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSeckillTimeAggregate;

import java.util.List;
import java.util.Optional;

public interface LitemallSeckillTimeRepository {

    List<LitemallSeckillTimeAggregate> findAllEnabled();

    Optional<LitemallSeckillTimeAggregate> findByHour(Byte hour);
}
