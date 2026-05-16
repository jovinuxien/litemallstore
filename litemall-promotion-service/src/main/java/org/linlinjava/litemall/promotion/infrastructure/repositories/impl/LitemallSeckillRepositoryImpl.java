package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSeckillAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSeckillRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSeckillStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallSeckillRepositoryImpl implements LitemallSeckillRepository {

    private final LitemallSeckillMapper seckillMapper;

    public LitemallSeckillRepositoryImpl(LitemallSeckillMapper seckillMapper) {
        this.seckillMapper = seckillMapper;
    }

    @Override
    public Optional<LitemallSeckillAggregate> findById(LitemallSeckillId seckillId) {
        LitemallSeckill entity = seckillMapper.selectByPrimaryKey(seckillId.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallSeckillAggregate> findActiveByHour(Byte hour) {
        return seckillMapper.selectActive().stream()
                .filter(s -> s.getTime() != null && s.getTime().equals(hour))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallSeckillAggregate> findAllActive() {
        return seckillMapper.selectActive().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int updateStock(LitemallSeckillId seckillId, int stockDelta) {
        if (stockDelta >= 0) return 0;
        return seckillMapper.decreaseStock(seckillId.getId(), -stockDelta);
    }

    @Override
    public void save(LitemallSeckillAggregate seckill) {
        if (seckill.getSeckillId() != null) {
            LitemallSeckill record = toData(seckill);
            seckillMapper.updateByPrimaryKeySelective(record);
        } else {
            LitemallSeckill record = toData(seckill);
            record.setAddTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            record.setDeleted(false);
            record.setIsDel(false);
            seckillMapper.insertSelective(record);
            if (record.getId() != null) {
                seckill.setSeckillId(new LitemallSeckillId(record.getId()));
            }
        }
    }

    private LitemallSeckillAggregate toDomain(LitemallSeckill r) {
        return LitemallSeckillAggregate.builder()
                .seckillId(new LitemallSeckillId(r.getId()))
                .goodsId(r.getGoodsId())
                .goodsName(r.getGoodsName())
                .price(r.getPrice() != null ? new LitemallMoney(r.getPrice()) : null)
                .stock(r.getStock())
                .sales(r.getSales())
                .quota(r.getQuota())
                .seckillTime(r.getTime())
                .status(r.getStatus() != null ? LitemallSeckillStatus.fromCode(r.getStatus()) : LitemallSeckillStatus.INACTIVE)
                .startTime(r.getStartTime())
                .stopTime(r.getStopTime())
                .build();
    }

    private LitemallSeckill toData(LitemallSeckillAggregate agg) {
        LitemallSeckill r = new LitemallSeckill();
        if (agg.getSeckillId() != null) r.setId(agg.getSeckillId().getId());
        r.setGoodsId(agg.getGoodsId());
        r.setGoodsName(agg.getGoodsName());
        r.setPrice(agg.getPrice() != null ? agg.getPrice().getAmount() : null);
        r.setStock(agg.getStock());
        r.setSales(agg.getSales());
        r.setQuota(agg.getQuota());
        r.setTime(agg.getSeckillTime());
        r.setStatus(agg.getStatus() != null ? (byte) agg.getStatus().getCode() : (byte) 0);
        r.setStartTime(agg.getStartTime());
        r.setStopTime(agg.getStopTime());
        return r;
    }
}