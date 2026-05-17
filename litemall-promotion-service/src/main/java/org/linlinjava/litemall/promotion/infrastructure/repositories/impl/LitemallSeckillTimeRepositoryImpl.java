package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallSeckillTimeMapper;
import org.linlinjava.litemall.db.domain.LitemallSeckillTime;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSeckillTimeAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSeckillTimeRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillTimeId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallSeckillTimeRepositoryImpl implements LitemallSeckillTimeRepository {

    private final LitemallSeckillTimeMapper seckillTimeMapper;

    public LitemallSeckillTimeRepositoryImpl(LitemallSeckillTimeMapper seckillTimeMapper) {
        this.seckillTimeMapper = seckillTimeMapper;
    }

    @Override
    public List<LitemallSeckillTimeAggregate> findAllEnabled() {
        return seckillTimeMapper.selectEnabled().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LitemallSeckillTimeAggregate> findByHour(Byte hour) {
        LitemallSeckillTime record = seckillTimeMapper.selectByHour(hour);
        return record != null ? Optional.of(toDomain(record)) : Optional.empty();
    }

    private LitemallSeckillTimeAggregate toDomain(LitemallSeckillTime r) {
        return LitemallSeckillTimeAggregate.builder()
                .timeId(new LitemallSeckillTimeId(r.getId()))
                .hour(r.getTime())
                .status(r.getStatus() != null && r.getStatus() == 1)
                .build();
    }
}