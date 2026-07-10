package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallAftersaleMapper;
import org.linlinjava.litemall.db.domain.LitemallAftersale;
import org.linlinjava.litemall.db.domain.LitemallAftersaleExample;
import org.linlinjava.litemall.db.util.AftersaleConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAftersaleRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** MyBatis adapter over the legacy {@code litemall_aftersale} table (V1 baseline). */
@Repository
public class LitemallAftersaleRepositoryImpl implements LitemallAftersaleRepository {

    /** Undecided states: applied (1) and approved-awaiting-refund (2). */
    private static final List<Short> OPEN_STATUSES = Arrays.asList(
            AftersaleConstant.STATUS_REQUEST, AftersaleConstant.STATUS_RECEPT);

    private final LitemallAftersaleMapper mapper;

    public LitemallAftersaleRepositoryImpl(LitemallAftersaleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LitemallAftersaleAggregate> findById(Integer aftersaleId) {
        LitemallAftersale row = mapper.selectByPrimaryKeyWithLogicalDelete(aftersaleId, false);
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public List<LitemallAftersaleAggregate> findByOrder(LitemallOrderId orderId) {
        LitemallAftersaleExample example = new LitemallAftersaleExample();
        example.or().andOrderIdEqualTo(orderId.getId()).andLogicalDeleted(false);
        example.setOrderByClause("add_time desc, id desc");
        return mapper.selectByExample(example).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LitemallAftersaleAggregate> findOpenByOrder(LitemallOrderId orderId) {
        LitemallAftersaleExample example = new LitemallAftersaleExample();
        example.or().andOrderIdEqualTo(orderId.getId())
                .andStatusIn(OPEN_STATUSES)
                .andLogicalDeleted(false);
        example.setOrderByClause("id desc");
        return mapper.selectByExample(example).stream().findFirst().map(this::toDomain);
    }

    @Override
    public long countByOrder(LitemallOrderId orderId) {
        // Counts soft-deleted rows too: the sn sequence must never reuse a number.
        LitemallAftersaleExample example = new LitemallAftersaleExample();
        example.or().andOrderIdEqualTo(orderId.getId());
        return mapper.countByExample(example);
    }

    @Override
    public List<LitemallAftersaleAggregate> adminQuery(Short status, Integer orderId, Integer userId,
                                                       int page, int limit) {
        PageHelper.startPage(page, limit);
        return mapper.selectByExample(adminExample(status, orderId, userId)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long adminCount(Short status, Integer orderId, Integer userId) {
        return mapper.countByExample(adminExample(status, orderId, userId));
    }

    private LitemallAftersaleExample adminExample(Short status, Integer orderId, Integer userId) {
        LitemallAftersaleExample example = new LitemallAftersaleExample();
        LitemallAftersaleExample.Criteria criteria = example.or().andLogicalDeleted(false);
        if (status != null) {
            criteria.andStatusEqualTo(status);
        }
        if (orderId != null) {
            criteria.andOrderIdEqualTo(orderId);
        }
        if (userId != null) {
            criteria.andUserIdEqualTo(userId);
        }
        example.setOrderByClause("add_time desc, id desc");
        return example;
    }

    @Override
    public LitemallAftersaleAggregate add(LitemallAftersaleAggregate aftersale) {
        LitemallAftersale row = toData(aftersale);
        row.setDeleted(false);
        mapper.insertSelective(row);
        aftersale.setId(row.getId());
        return aftersale;
    }

    @Override
    public void update(LitemallAftersaleAggregate aftersale) {
        mapper.updateByPrimaryKeySelective(toData(aftersale));
    }

    // ---- mapping ----------------------------------------------------------------

    private LitemallAftersaleAggregate toDomain(LitemallAftersale row) {
        LitemallAftersaleAggregate agg = new LitemallAftersaleAggregate();
        agg.setId(row.getId());
        agg.setAftersaleSn(row.getAftersaleSn());
        agg.setOrderId(new LitemallOrderId(row.getOrderId()));
        agg.setUserId(new LitemallUserId(row.getUserId()));
        agg.setType(row.getType());
        agg.setReason(row.getReason());
        agg.setAmount(new LitemallMoney(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount()));
        agg.setPictures(row.getPictures());
        agg.setComment(row.getComment());
        agg.setStatus(LitemallAfterSaleStatus.fromStatusCode(
                row.getStatus() == null ? 0 : row.getStatus()));
        agg.setHandleTime(row.getHandleTime());
        agg.setAddTime(row.getAddTime());
        agg.setUpdateTime(row.getUpdateTime());
        return agg;
    }

    private LitemallAftersale toData(LitemallAftersaleAggregate agg) {
        LitemallAftersale row = new LitemallAftersale();
        row.setId(agg.getId());
        row.setAftersaleSn(agg.getAftersaleSn());
        row.setOrderId(agg.getOrderId() == null ? null : agg.getOrderId().getId());
        row.setUserId(agg.getUserId() == null ? null : agg.getUserId().getId());
        row.setType(agg.getType());
        row.setReason(agg.getReason());
        row.setAmount(agg.getAmount() == null ? null : agg.getAmount().getAmount());
        row.setPictures(agg.getPictures());
        row.setComment(agg.getComment());
        row.setStatus(agg.getStatus() == null ? null : agg.getStatus().getCode());
        row.setHandleTime(agg.getHandleTime());
        row.setAddTime(agg.getAddTime());
        row.setUpdateTime(agg.getUpdateTime());
        return row;
    }
}
