package org.linlinjava.litemall.loyalty.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserSignMapper;
import org.linlinjava.litemall.db.domain.LitemallUserSign;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSignInAggregate;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallSignInRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallSignId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallSignInRepositoryImpl implements LitemallSignInRepository {

    private final LitemallUserSignMapper signMapper;

    public LitemallSignInRepositoryImpl(LitemallUserSignMapper signMapper) {
        this.signMapper = signMapper;
    }

    @Override
    public void add(LitemallSignInAggregate signIn) {
        LitemallUserSign entity = new LitemallUserSign();
        entity.setUserId(signIn.getUserId().getId());
        entity.setIntegral(signIn.getIntegral());
        entity.setAddTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(false);
        signMapper.insertSelective(entity);
        if (entity.getId() != null) {
            signIn.setSignId(new LitemallSignId(entity.getId()));
        }
    }

    @Override
    public boolean hasTodaySigned(LitemallUserId userId) {
        return signMapper.countTodayByUserId(userId.getId()) > 0;
    }

    @Override
    public List<LitemallSignInAggregate> findByUserId(LitemallUserId userId) {
        return signMapper.selectByUserId(userId.getId()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countByUserId(LitemallUserId userId) {
        return signMapper.selectByUserId(userId.getId()).size();
    }

    private LitemallSignInAggregate toDomain(LitemallUserSign r) {
        LitemallSignInAggregate agg = new LitemallSignInAggregate();
        if (r.getId() != null) agg.setSignId(new LitemallSignId(r.getId()));
        agg.setUserId(new LitemallUserId(r.getUserId()));
        agg.setIntegral(r.getIntegral());
        agg.setSignDate(r.getAddTime());
        agg.setAddTime(r.getAddTime());
        agg.setUpdateTime(r.getUpdateTime());
        return agg;
    }
}