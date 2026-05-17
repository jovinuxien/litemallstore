package org.linlinjava.litemall.loyalty.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallSystemUserLevelMapper;
import org.linlinjava.litemall.db.dao.LitemallUserLevelMapper;
import org.linlinjava.litemall.db.domain.LitemallSystemUserLevel;
import org.linlinjava.litemall.db.domain.LitemallUserLevel;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallSystemLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.aggregates.LitemallUserLevelAggregate;
import org.linlinjava.litemall.loyalty.domain.model.repositories.LitemallUserLevelRepository;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallSystemLevelId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserLevelRecordId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallUserLevelRepositoryImpl implements LitemallUserLevelRepository {

    private final LitemallSystemUserLevelMapper systemLevelMapper;
    private final LitemallUserLevelMapper userLevelMapper;

    public LitemallUserLevelRepositoryImpl(LitemallSystemUserLevelMapper systemLevelMapper,
                                           LitemallUserLevelMapper userLevelMapper) {
        this.systemLevelMapper = systemLevelMapper;
        this.userLevelMapper = userLevelMapper;
    }

    @Override
    public Optional<LitemallUserLevelAggregate> findCurrentByUserId(LitemallUserId userId) {
        LitemallUserLevel record = userLevelMapper.selectCurrentByUserId(userId.getId());
        return record != null ? Optional.of(toDomainUserLevel(record)) : Optional.empty();
    }

    @Override
    public void save(LitemallUserLevelAggregate level) {
        if (level.getLevelRecordId() != null) {
            LitemallUserLevel record = toDataUserLevel(level);
            userLevelMapper.updateByPrimaryKeySelective(record);
        } else {
            LitemallUserLevel record = toDataUserLevel(level);
            record.setAddTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            record.setDeleted(false);
            userLevelMapper.insertSelective(record);
            if (record.getId() != null) {
                level.setLevelRecordId(new LitemallUserLevelRecordId(record.getId()));
            }
        }
    }

    @Override
    public List<LitemallSystemLevelAggregate> findAllSystemLevels() {
        return systemLevelMapper.selectAll().stream()
                .map(this::toDomainSystemLevel)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LitemallSystemLevelAggregate> findNextLevelByExperience(int experience) {
        return findAllSystemLevels().stream()
                .filter(l -> l.getRequiredExperience() != null && l.getRequiredExperience() <= experience)
                .max((a, b) -> {
                    int ea = a.getRequiredExperience() != null ? a.getRequiredExperience() : 0;
                    int eb = b.getRequiredExperience() != null ? b.getRequiredExperience() : 0;
                    return Integer.compare(ea, eb);
                });
    }

    private LitemallUserLevelAggregate toDomainUserLevel(LitemallUserLevel r) {
        LitemallUserLevelAggregate agg = new LitemallUserLevelAggregate();
        agg.setLevelRecordId(new LitemallUserLevelRecordId(r.getId()));
        agg.setUserId(new LitemallUserId(r.getUserId()));
        agg.setLevelId(r.getLevelId());
        agg.setGrade(r.getGrade());
        agg.setExperience(r.getExperience());
        agg.setStatus(r.getStatus());
        agg.setAddTime(r.getAddTime());
        agg.setUpdateTime(r.getUpdateTime());
        return agg;
    }

    private LitemallUserLevel toDataUserLevel(LitemallUserLevelAggregate agg) {
        LitemallUserLevel r = new LitemallUserLevel();
        if (agg.getLevelRecordId() != null) r.setId(agg.getLevelRecordId().getId());
        r.setUserId(agg.getUserId().getId());
        r.setLevelId(agg.getLevelId());
        r.setGrade(agg.getGrade());
        r.setExperience(agg.getExperience());
        r.setStatus(agg.getStatus());
        return r;
    }

    private LitemallSystemLevelAggregate toDomainSystemLevel(LitemallSystemUserLevel r) {
        LitemallSystemLevelAggregate agg = new LitemallSystemLevelAggregate();
        agg.setSystemLevelId(new LitemallSystemLevelId(r.getId()));
        agg.setName(r.getName());
        agg.setLevel(r.getLevel());
        agg.setRequiredExperience(r.getExperience());
        agg.setDiscount(r.getDiscount());
        agg.setIcon(r.getIcon());
        return agg;
    }
}