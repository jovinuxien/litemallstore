package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallBargainMapper;
import org.linlinjava.litemall.db.domain.LitemallBargain;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallBargainRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallBargainStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallBargainRepositoryImpl implements LitemallBargainRepository {

    private final LitemallBargainMapper bargainMapper;

    public LitemallBargainRepositoryImpl(LitemallBargainMapper bargainMapper) {
        this.bargainMapper = bargainMapper;
    }

    @Override
    public Optional<LitemallBargainAggregate> findById(LitemallBargainId bargainId) {
        LitemallBargain entity = bargainMapper.selectByPrimaryKey(bargainId.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallBargainAggregate> findAllActive() {
        return bargainMapper.selectActive().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(LitemallBargainAggregate bargain) {
        if (bargain.getBargainId() != null) {
            LitemallBargain record = toData(bargain);
            bargainMapper.updateByPrimaryKeySelective(record);
        } else {
            LitemallBargain record = toData(bargain);
            record.setAddTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            record.setDeleted(false);
            record.setIsDel(false);
            record.setSales(0);
            bargainMapper.insertSelective(record);
            if (record.getId() != null) {
                bargain.setBargainId(new LitemallBargainId(record.getId()));
            }
        }
    }

    private LitemallBargainAggregate toDomain(LitemallBargain r) {
        return LitemallBargainAggregate.builder()
                .bargainId(new LitemallBargainId(r.getId()))
                .goodsId(r.getGoodsId())
                .title(r.getTitle())
                .price(r.getPrice() != null ? new LitemallMoney(r.getPrice()) : null)
                .minPrice(r.getMinPrice() != null ? new LitemallMoney(r.getMinPrice()) : null)
                .bargainMaxPrice(r.getBargainMaxPrice() != null ? new LitemallMoney(r.getBargainMaxPrice()) : null)
                .bargainMinPrice(r.getBargainMinPrice() != null ? new LitemallMoney(r.getBargainMinPrice()) : null)
                .bargainNum(r.getBargainNum())
                .peopleNum(r.getPeopleNum())
                .stock(r.getStock())
                .status(r.getStatus() != null ? LitemallBargainStatus.fromCode(r.getStatus()) : LitemallBargainStatus.INACTIVE)
                .startTime(r.getStartTime())
                .stopTime(r.getStopTime())
                .build();
    }

    private LitemallBargain toData(LitemallBargainAggregate agg) {
        LitemallBargain r = new LitemallBargain();
        if (agg.getBargainId() != null) r.setId(agg.getBargainId().getId());
        r.setGoodsId(agg.getGoodsId());
        r.setTitle(agg.getTitle());
        r.setPrice(agg.getPrice() != null ? agg.getPrice().getAmount() : null);
        r.setMinPrice(agg.getMinPrice() != null ? agg.getMinPrice().getAmount() : null);
        r.setBargainMaxPrice(agg.getBargainMaxPrice() != null ? agg.getBargainMaxPrice().getAmount() : null);
        r.setBargainMinPrice(agg.getBargainMinPrice() != null ? agg.getBargainMinPrice().getAmount() : null);
        r.setBargainNum(agg.getBargainNum());
        r.setPeopleNum(agg.getPeopleNum());
        r.setStock(agg.getStock());
        r.setStatus(agg.getStatus() != null ? (byte) agg.getStatus().getCode() : (byte) 0);
        r.setStartTime(agg.getStartTime());
        r.setStopTime(agg.getStopTime());
        return r;
    }
}