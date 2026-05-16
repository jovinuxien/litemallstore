package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallBargainUserMapper;
import org.linlinjava.litemall.db.domain.LitemallBargainUser;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainUserAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallBargainUserRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallBargainUserStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class LitemallBargainUserRepositoryImpl implements LitemallBargainUserRepository {

    private final LitemallBargainUserMapper bargainUserMapper;

    public LitemallBargainUserRepositoryImpl(LitemallBargainUserMapper bargainUserMapper) {
        this.bargainUserMapper = bargainUserMapper;
    }

    @Override
    public void add(LitemallBargainUserAggregate bargainUser) {
        LitemallBargainUser entity = toData(bargainUser);
        entity.setAddTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(false);
        bargainUserMapper.insertSelective(entity);
        if (entity.getId() != null) {
            bargainUser.setBargainUserId(new LitemallBargainUserId(entity.getId()));
        }
    }

    @Override
    public Optional<LitemallBargainUserAggregate> findById(LitemallBargainUserId id) {
        LitemallBargainUser entity = bargainUserMapper.selectByPrimaryKey(id.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public Optional<LitemallBargainUserAggregate> findByUserAndBargain(LitemallUserId userId,
                                                                        LitemallBargainId bargainId) {
        LitemallBargainUser entity = bargainUserMapper.selectByUserIdAndBargainId(
                userId.getId(), bargainId.getId());
        return entity != null ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public int updateStatus(LitemallBargainUserId id, LitemallBargainUserStatus status) {
        LitemallBargainUser entity = new LitemallBargainUser();
        entity.setId(id.getId());
        entity.setStatus((byte) status.getCode());
        return bargainUserMapper.updateByPrimaryKeySelective(entity);
    }

    @Override
    public int updateBargainPrice(LitemallBargainUserId id, LitemallMoney newPrice) {
        LitemallBargainUser entity = new LitemallBargainUser();
        entity.setId(id.getId());
        entity.setBargainPrice(newPrice.getAmount());
        return bargainUserMapper.updateByPrimaryKeySelective(entity);
    }

    private LitemallBargainUserAggregate toDomain(LitemallBargainUser r) {
        return LitemallBargainUserAggregate.builder()
                .bargainUserId(new LitemallBargainUserId(r.getId()))
                .userId(new LitemallUserId(r.getUserId()))
                .bargainId(new LitemallBargainId(r.getBargainId()))
                .bargainPriceMin(r.getBargainPriceMin() != null ? new LitemallMoney(r.getBargainPriceMin()) : null)
                .bargainPrice(r.getBargainPrice() != null ? new LitemallMoney(r.getBargainPrice()) : null)
                .status(r.getStatus() != null ? LitemallBargainUserStatus.fromCode(r.getStatus()) : LitemallBargainUserStatus.ONGOING)
                .build();
    }

    private LitemallBargainUser toData(LitemallBargainUserAggregate agg) {
        LitemallBargainUser r = new LitemallBargainUser();
        if (agg.getBargainUserId() != null) r.setId(agg.getBargainUserId().getId());
        r.setUserId(agg.getUserId().getId());
        r.setBargainId(agg.getBargainId().getId());
        r.setBargainPriceMin(agg.getBargainPriceMin() != null ? agg.getBargainPriceMin().getAmount() : null);
        r.setBargainPrice(agg.getBargainPrice() != null ? agg.getBargainPrice().getAmount() : null);
        r.setStatus(agg.getStatus() != null ? (byte) agg.getStatus().getCode() : (byte) 0);
        return r;
    }
}