package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallCouponMapper;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponExample;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallCouponRepositoryImpl implements LitemallCouponRepository {

    private final LitemallCouponMapper couponMapper;

    public LitemallCouponRepositoryImpl(LitemallCouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    @Override
    public Optional<LitemallCouponAggregate> findById(LitemallCouponId couponId) {
        LitemallCoupon entity = couponMapper.selectByPrimaryKey(couponId.getId());
        return entity != null && !Boolean.TRUE.equals(entity.getDeleted())
                ? Optional.of(toDomain(entity)) : Optional.empty();
    }

    @Override
    public List<LitemallCouponAggregate> findReceivable() {
        LitemallCouponExample example = new LitemallCouponExample();
        example.or()
                .andDeletedEqualTo(false)
                .andStatusEqualTo((short) LitemallCouponStatus.NORMAL.getCode());
        example.setOrderByClause("add_time DESC");
        return couponMapper.selectByExample(example).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallCouponAggregate> findAll(int page, int limit) {
        LitemallCouponExample example = new LitemallCouponExample();
        example.or().andDeletedEqualTo(false);
        example.setOrderByClause("add_time DESC");
        PageHelper.startPage(page, limit);
        return couponMapper.selectByExample(example).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void save(LitemallCouponAggregate coupon) {
        LitemallCoupon record = toData(coupon);
        if (coupon.getCouponId() != null) {
            record.setUpdateTime(LocalDateTime.now());
            couponMapper.updateByPrimaryKeySelective(record);
        } else {
            LocalDateTime now = LocalDateTime.now();
            record.setAddTime(now);
            record.setUpdateTime(now);
            record.setDeleted(false);
            couponMapper.insertSelective(record);
            if (record.getId() != null) {
                coupon.setCouponId(new LitemallCouponId(record.getId()));
            }
        }
    }

    private LitemallCouponAggregate toDomain(LitemallCoupon r) {
        return LitemallCouponAggregate.builder()
                .couponId(new LitemallCouponId(r.getId()))
                .name(r.getName())
                .description(r.getDesc())
                .tag(r.getTag())
                .total(r.getTotal())
                .discount(r.getDiscount() != null ? new LitemallMoney(r.getDiscount()) : null)
                .min(r.getMin() != null ? new LitemallMoney(r.getMin()) : null)
                .limitPerUser(r.getLimit() != null ? r.getLimit().intValue() : null)
                .type(r.getType() != null ? LitemallCouponType.fromCode(r.getType()) : LitemallCouponType.COMMON)
                .status(r.getStatus() != null ? LitemallCouponStatus.fromCode(r.getStatus()) : LitemallCouponStatus.NORMAL)
                .goodsType(r.getGoodsType() != null ? LitemallCouponGoodsType.fromCode(r.getGoodsType()) : LitemallCouponGoodsType.ALL)
                .goodsValue(r.getGoodsValue())
                .code(r.getCode())
                .timeType(r.getTimeType() != null ? LitemallCouponTimeType.fromCode(r.getTimeType()) : LitemallCouponTimeType.DAYS)
                .days(r.getDays() != null ? r.getDays().intValue() : null)
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .build();
    }

    private LitemallCoupon toData(LitemallCouponAggregate agg) {
        LitemallCoupon r = new LitemallCoupon();
        if (agg.getCouponId() != null) r.setId(agg.getCouponId().getId());
        r.setName(agg.getName());
        r.setDesc(agg.getDescription());
        r.setTag(agg.getTag());
        r.setTotal(agg.getTotal());
        r.setDiscount(agg.getDiscount() != null ? agg.getDiscount().getAmount() : null);
        r.setMin(agg.getMin() != null ? agg.getMin().getAmount() : null);
        r.setLimit(agg.getLimitPerUser() != null ? agg.getLimitPerUser().shortValue() : null);
        r.setType(agg.getType() != null ? (short) agg.getType().getCode() : null);
        r.setStatus(agg.getStatus() != null ? (short) agg.getStatus().getCode() : (short) LitemallCouponStatus.NORMAL.getCode());
        r.setGoodsType(agg.getGoodsType() != null ? (short) agg.getGoodsType().getCode() : null);
        r.setGoodsValue(agg.getGoodsValue());
        r.setCode(agg.getCode());
        r.setTimeType(agg.getTimeType() != null ? (short) agg.getTimeType().getCode() : null);
        r.setDays(agg.getDays() != null ? agg.getDays().shortValue() : null);
        r.setStartTime(agg.getStartTime());
        r.setEndTime(agg.getEndTime());
        return r;
    }
}
