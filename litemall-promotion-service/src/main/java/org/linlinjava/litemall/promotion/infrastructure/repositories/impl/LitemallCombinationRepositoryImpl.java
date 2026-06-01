package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallCombinationMapper;
import org.linlinjava.litemall.db.domain.LitemallCombination;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCombinationRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallCombinationRepositoryImpl implements LitemallCombinationRepository {

    private final LitemallCombinationMapper combinationMapper;

    public LitemallCombinationRepositoryImpl(LitemallCombinationMapper combinationMapper) {
        this.combinationMapper = combinationMapper;
    }

    @Override
    public Optional<LitemallCombinationAggregate> findById(LitemallCombinationId combinationId) {
        LitemallCombination entity = combinationMapper.selectByPrimaryKey(combinationId.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallCombinationAggregate> findActive() {
        return combinationMapper.selectActive().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallCombinationAggregate> findAll() {
        return combinationMapper.selectAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(LitemallCombinationAggregate combination) {
        LitemallCombination record = toData(combination);
        if (combination.getCombinationId() != null) {
            record.setUpdateTime(LocalDateTime.now());
            combinationMapper.updateByPrimaryKeySelective(record);
        } else {
            LocalDateTime now = LocalDateTime.now();
            record.setAddTime(now);
            record.setUpdateTime(now);
            record.setDeleted(false);
            combinationMapper.insertSelective(record);
            if (record.getId() != null) {
                combination.setCombinationId(new LitemallCombinationId(record.getId()));
            }
        }
    }

    private LitemallCombinationAggregate toDomain(LitemallCombination r) {
        return LitemallCombinationAggregate.builder()
                .combinationId(new LitemallCombinationId(r.getId()))
                .goodsId(r.getGoodsId())
                .title(r.getTitle())
                .picUrl(r.getPicUrl())
                .combinationPrice(r.getCombinationPrice() != null ? new LitemallMoney(r.getCombinationPrice()) : null)
                .originalPrice(r.getOriginalPrice() != null ? new LitemallMoney(r.getOriginalPrice()) : null)
                .requiredMembers(r.getRequiredMembers())
                .limitPerUser(r.getLimitPerUser())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .status(r.getStatus() != null ? LitemallCombinationStatus.fromCode(r.getStatus()) : LitemallCombinationStatus.DRAFT)
                .build();
    }

    private LitemallCombination toData(LitemallCombinationAggregate agg) {
        LitemallCombination r = new LitemallCombination();
        if (agg.getCombinationId() != null) r.setId(agg.getCombinationId().getId());
        r.setGoodsId(agg.getGoodsId());
        r.setTitle(agg.getTitle());
        r.setPicUrl(agg.getPicUrl());
        r.setCombinationPrice(agg.getCombinationPrice() != null ? agg.getCombinationPrice().getAmount() : null);
        r.setOriginalPrice(agg.getOriginalPrice() != null ? agg.getOriginalPrice().getAmount() : null);
        r.setRequiredMembers(agg.getRequiredMembers());
        r.setLimitPerUser(agg.getLimitPerUser());
        r.setStartTime(agg.getStartTime());
        r.setEndTime(agg.getEndTime());
        r.setStatus(agg.getStatus() != null ? (short) agg.getStatus().getCode() : (short) LitemallCombinationStatus.DRAFT.getCode());
        return r;
    }
}
