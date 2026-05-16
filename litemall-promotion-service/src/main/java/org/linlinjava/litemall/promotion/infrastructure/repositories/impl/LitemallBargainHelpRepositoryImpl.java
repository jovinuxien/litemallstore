package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallBargainHelpMapper;
import org.linlinjava.litemall.db.domain.LitemallBargainHelp;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainHelpAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallBargainHelpRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainHelpId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallBargainHelpRepositoryImpl implements LitemallBargainHelpRepository {

    private final LitemallBargainHelpMapper bargainHelpMapper;

    public LitemallBargainHelpRepositoryImpl(LitemallBargainHelpMapper bargainHelpMapper) {
        this.bargainHelpMapper = bargainHelpMapper;
    }

    @Override
    public void add(LitemallBargainHelpAggregate help) {
        LitemallBargainHelp entity = toData(help);
        entity.setAddTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(false);
        bargainHelpMapper.insertSelective(entity);
        if (entity.getId() != null) {
            help.setHelpId(new LitemallBargainHelpId(entity.getId()));
        }
    }

    @Override
    public int countByBargainUserId(LitemallBargainUserId bargainUserId) {
        return bargainHelpMapper.countByBargainUserId(bargainUserId.getId());
    }

    @Override
    public List<LitemallBargainHelpAggregate> findByBargainUserId(LitemallBargainUserId bargainUserId) {
        return bargainHelpMapper.selectByBargainUserId(bargainUserId.getId()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private LitemallBargainHelpAggregate toDomain(LitemallBargainHelp r) {
        return LitemallBargainHelpAggregate.builder()
                .helpId(new LitemallBargainHelpId(r.getId()))
                .helperId(new LitemallUserId(r.getUserId()))
                .bargainId(new LitemallBargainId(r.getBargainId()))
                .bargainUserId(new LitemallBargainUserId(r.getBargainUserId()))
                .helpAmount(r.getPrice() != null ? new LitemallMoney(r.getPrice()) : null)
                .build();
    }

    private LitemallBargainHelp toData(LitemallBargainHelpAggregate agg) {
        LitemallBargainHelp r = new LitemallBargainHelp();
        if (agg.getHelpId() != null) r.setId(agg.getHelpId().getId());
        r.setUserId(agg.getHelperId().getId());
        r.setBargainId(agg.getBargainId().getId());
        r.setBargainUserId(agg.getBargainUserId().getId());
        r.setPrice(agg.getHelpAmount() != null ? agg.getHelpAmount().getAmount() : null);
        return r;
    }
}