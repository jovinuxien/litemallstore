package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
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
    public List<LitemallUserCouponAggregate> findByUser(LitemallUserId userId, LitemallUserCouponStatus status) {
        LitemallCouponUserExample example = new LitemallCouponUserExample();
        LitemallCouponUserExample.Criteria criteria = example.or()
                .andDeletedEqualTo(false)
                .andUserIdEqualTo(userId.getId());
        if (status != null) {
            criteria.andStatusEqualTo((short) status.getCode());
        }
        example.setOrderByClause("add_time DESC");
        return couponUserMapper.selectByExample(example).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallUserCouponAggregate> findByCoupon(LitemallCouponId couponId, int page, int limit) {
        LitemallCouponUserExample example = new LitemallCouponUserExample();
        example.or()
                .andDeletedEqualTo(false)
                .andCouponIdEqualTo(couponId.getId());
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(page, limit);
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
        // Full-row update: a release CLEARS order_id/used_time, and the
        // selective update would silently skip those nulls.
        LitemallCouponUser current = couponUserMapper.selectByPrimaryKey(
                userCoupon.getUserCouponId().getId());
        LitemallCouponUser record = toData(userCoupon);
        if (current != null) {
            record.setAddTime(current.getAddTime());
            record.setDeleted(current.getDeleted());
        }
        record.setUpdateTime(LocalDateTime.now());
        couponUserMapper.updateByPrimaryKey(record);
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
