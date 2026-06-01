package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallCouponUserMapper;
import org.linlinjava.litemall.db.domain.LitemallCouponUser;
import org.linlinjava.litemall.db.domain.LitemallCouponUserExample;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallUserCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallUserCouponRepositoryImpl implements LitemallUserCouponRepository {

    private final LitemallCouponUserMapper couponUserMapper;

    public LitemallUserCouponRepositoryImpl(LitemallCouponUserMapper couponUserMapper) {
        this.couponUserMapper = couponUserMapper;
    }

    @Override
    public Optional<LitemallUserCouponAggregate> findById(LitemallUserCouponId id) {
        LitemallCouponUser entity = couponUserMapper.selectByPrimaryKey(id.getId());
        return entity != null && !Boolean.TRUE.equals(entity.getDeleted())
                ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public int countByUserAndCoupon(LitemallUserId userId, LitemallCouponId couponId) {
        LitemallCouponUserExample example = new LitemallCouponUserExample();
        example.or()
                .andDeletedEqualTo(false)
                .andUserIdEqualTo(userId.getId())
                .andCouponIdEqualTo(couponId.getId());
        return (int) couponUserMapper.countByExample(example);
    }

    @Override
    public int countByCoupon(LitemallCouponId couponId) {
        LitemallCouponUserExample example = new LitemallCouponUserExample();
        example.or()
                .andDeletedEqualTo(false)
                .andCouponIdEqualTo(couponId.getId());
        return (int) couponUserMapper.countByExample(example);
    }

    @Override
    public List<LitemallUserCouponAggregate> findUsableByUser(LitemallUserId userId) {
        LitemallCouponUserExample example = new LitemallCouponUserExample();
        example.or()
                .andDeletedEqualTo(false)
                .andUserIdEqualTo(userId.getId())
                .andStatusEqualTo((short) LitemallUserCouponStatus.USABLE.getCode());
        example.setOrderByClause("add_time DESC");
        return couponUserMapper.selectByExample(example).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void add(LitemallUserCouponAggregate userCoupon) {
        LitemallCouponUser record = toData(userCoupon);
        LocalDateTime now = LocalDateTime.now();
        record.setAddTime(now);
        record.setUpdateTime(now);
        record.setDeleted(false);
        couponUserMapper.insertSelective(record);
        if (record.getId() != null) {
            userCoupon.setUserCouponId(new LitemallUserCouponId(record.getId()));
        }
    }

    @Override
    public void update(LitemallUserCouponAggregate userCoupon) {
        LitemallCouponUser record = toData(userCoupon);
        record.setUpdateTime(LocalDateTime.now());
        couponUserMapper.updateByPrimaryKeySelective(record);
    }

    private LitemallUserCouponAggregate toDomain(LitemallCouponUser r) {
        return LitemallUserCouponAggregate.builder()
                .userCouponId(new LitemallUserCouponId(r.getId()))
                .userId(new LitemallUserId(r.getUserId()))
                .couponId(new LitemallCouponId(r.getCouponId()))
                .status(r.getStatus() != null
                        ? LitemallUserCouponStatus.fromCode(r.getStatus())
                        : LitemallUserCouponStatus.USABLE)
                .usedTime(r.getUsedTime())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .orderId(r.getOrderId())
                .build();
    }

    private LitemallCouponUser toData(LitemallUserCouponAggregate agg) {
        LitemallCouponUser r = new LitemallCouponUser();
        if (agg.getUserCouponId() != null) r.setId(agg.getUserCouponId().getId());
        if (agg.getUserId() != null) r.setUserId(agg.getUserId().getId());
        if (agg.getCouponId() != null) r.setCouponId(agg.getCouponId().getId());
        r.setStatus(agg.getStatus() != null ? (short) agg.getStatus().getCode()
                : (short) LitemallUserCouponStatus.USABLE.getCode());
        r.setUsedTime(agg.getUsedTime());
        r.setStartTime(agg.getStartTime());
        r.setEndTime(agg.getEndTime());
        r.setOrderId(agg.getOrderId());
        return r;
    }
}
