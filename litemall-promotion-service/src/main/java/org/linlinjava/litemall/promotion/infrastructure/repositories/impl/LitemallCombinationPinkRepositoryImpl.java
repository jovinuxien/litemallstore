package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallCombinationPinkMapper;
import org.linlinjava.litemall.db.domain.LitemallCombinationPink;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCombinationPinkAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCombinationPinkRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationPinkStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallCombinationPinkRepositoryImpl implements LitemallCombinationPinkRepository {

    private final LitemallCombinationPinkMapper pinkMapper;

    public LitemallCombinationPinkRepositoryImpl(LitemallCombinationPinkMapper pinkMapper) {
        this.pinkMapper = pinkMapper;
    }

    @Override
    public Optional<LitemallCombinationPinkAggregate> findById(LitemallCombinationPinkId pinkId) {
        LitemallCombinationPink entity = pinkMapper.selectByPrimaryKey(pinkId.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallCombinationPinkAggregate> findGroup(LitemallCombinationPinkId headId) {
        return pinkMapper.selectGroup(headId.getId()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countGroup(LitemallCombinationPinkId headId) {
        return pinkMapper.countGroup(headId.getId());
    }

    @Override
    public List<LitemallCombinationPinkAggregate> findByUser(LitemallUserId userId) {
        return pinkMapper.selectByUser(userId.getId()).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallCombinationPinkAggregate> findExpiredPendingLeaders(LocalDateTime now) {
        return pinkMapper.selectExpiredPendingLeaders(now).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallCombinationPinkAggregate> findLeaders(LitemallCombinationId combinationId,
                                                              LitemallCombinationPinkStatus status) {
        return pinkMapper.selectLeaders(
                        combinationId != null ? combinationId.getId() : null,
                        status != null ? status.getCode() : null).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void add(LitemallCombinationPinkAggregate pink) {
        LitemallCombinationPink record = toData(pink);
        LocalDateTime now = LocalDateTime.now();
        record.setAddTime(now);
        record.setUpdateTime(now);
        record.setDeleted(false);
        pinkMapper.insert(record);
        if (record.getId() != null) {
            pink.setPinkId(new LitemallCombinationPinkId(record.getId()));
        }
    }

    @Override
    public void update(LitemallCombinationPinkAggregate pink) {
        LitemallCombinationPink record = toData(pink);
        record.setUpdateTime(LocalDateTime.now());
        pinkMapper.update(record);
    }

    private LitemallCombinationPinkAggregate toDomain(LitemallCombinationPink r) {
        return LitemallCombinationPinkAggregate.builder()
                .pinkId(new LitemallCombinationPinkId(r.getId()))
                .combinationId(new LitemallCombinationId(r.getCombinationId()))
                .headId(r.getHeadId() != null && r.getHeadId() > 0
                        ? new LitemallCombinationPinkId(r.getHeadId()) : null)
                .userId(new LitemallUserId(r.getUserId()))
                .orderId(r.getOrderId())
                .requiredMembers(r.getRequiredMembers())
                .expireTime(r.getExpireTime())
                .status(r.getStatus() != null
                        ? LitemallCombinationPinkStatus.fromCode(r.getStatus())
                        : LitemallCombinationPinkStatus.PENDING)
                .build();
    }

    private LitemallCombinationPink toData(LitemallCombinationPinkAggregate agg) {
        LitemallCombinationPink r = new LitemallCombinationPink();
        if (agg.getPinkId() != null) r.setId(agg.getPinkId().getId());
        if (agg.getCombinationId() != null) r.setCombinationId(agg.getCombinationId().getId());
        r.setHeadId(agg.getHeadId() != null ? agg.getHeadId().getId() : 0);
        if (agg.getUserId() != null) r.setUserId(agg.getUserId().getId());
        r.setOrderId(agg.getOrderId());
        r.setRequiredMembers(agg.getRequiredMembers());
        r.setExpireTime(agg.getExpireTime());
        r.setStatus(agg.getStatus() != null ? agg.getStatus().getCode()
                : LitemallCombinationPinkStatus.PENDING.getCode());
        return r;
    }
}
