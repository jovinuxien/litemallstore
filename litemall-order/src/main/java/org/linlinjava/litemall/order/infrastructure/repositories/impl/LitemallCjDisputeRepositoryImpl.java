package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.CjDisputeMapper;
import org.linlinjava.litemall.db.domain.LitemallCjDispute;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCjDisputeAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCjDisputeRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeResolution;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallCjDisputeRepositoryImpl implements LitemallCjDisputeRepository {

    private final CjDisputeMapper cjDisputeMapper;

    public LitemallCjDisputeRepositoryImpl(CjDisputeMapper cjDisputeMapper) {
        this.cjDisputeMapper = cjDisputeMapper;
    }

    @Override
    public void add(LitemallCjDisputeAggregate dispute) {
        LitemallCjDispute row = toRow(dispute);
        row.setAddTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleted(false);
        cjDisputeMapper.insert(row);
        dispute.setDisputeId(row.getId());
    }

    @Override
    public Optional<LitemallCjDisputeAggregate> findById(Integer disputeId) {
        LitemallCjDispute row = cjDisputeMapper.selectById(disputeId);
        return row == null ? Optional.empty() : Optional.of(toAggregate(row));
    }

    @Override
    public List<LitemallCjDisputeAggregate> findByOrder(LitemallOrderId orderId) {
        return cjDisputeMapper.selectByOrderId(orderId.getId()).stream()
                .map(this::toAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public List<LitemallCjDisputeAggregate> findOpenByOrder(LitemallOrderId orderId) {
        return cjDisputeMapper.selectOpenByOrderId(orderId.getId()).stream()
                .map(this::toAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public void updateCjProjection(LitemallCjDisputeAggregate dispute) {
        LitemallCjDispute patch = new LitemallCjDispute();
        patch.setId(dispute.getDisputeId());
        patch.setCjDisputeId(dispute.getCjDisputeId());
        patch.setStatus(dispute.getCjStatus());
        patch.setFinallyDeal(dispute.getResolution() == null ? null : dispute.getResolution().getCjCode());
        patch.setRefundAmount(dispute.getRefundAmountUsd());
        patch.setResendOrderCode(dispute.getResendOrderCode());
        patch.setUpdateTime(LocalDateTime.now());
        cjDisputeMapper.updateCjProjection(patch);
    }

    @Override
    public void markCancelled(Integer disputeId) {
        cjDisputeMapper.markCancelled(disputeId);
    }

    private LitemallCjDispute toRow(LitemallCjDisputeAggregate agg) {
        LitemallCjDispute row = new LitemallCjDispute();
        row.setId(agg.getDisputeId());
        row.setOrderId(agg.getOrderId().getId());
        row.setUserId(agg.getUserId().getId());
        row.setCjOrderId(agg.getCjOrderId());
        row.setBusinessDisputeId(agg.getBusinessDisputeId());
        row.setCjDisputeId(agg.getCjDisputeId());
        row.setReasonId(agg.getReasonId());
        row.setReasonName(agg.getReasonName());
        row.setExpectType(agg.getExpectation() == null ? null : agg.getExpectation().getCjCode());
        row.setMessage(agg.getMessage() == null ? "" : agg.getMessage());
        row.setImageUrls(agg.getImageUrls() == null || agg.getImageUrls().isEmpty()
                ? null : String.join(",", agg.getImageUrls()));
        row.setStatus(agg.getCjStatus());
        row.setFinallyDeal(agg.getResolution() == null ? null : agg.getResolution().getCjCode());
        row.setRefundAmount(agg.getRefundAmountUsd());
        row.setResendOrderCode(agg.getResendOrderCode());
        row.setCancelled(agg.isCancelled());
        return row;
    }

    private LitemallCjDisputeAggregate toAggregate(LitemallCjDispute row) {
        LitemallCjDisputeAggregate agg = new LitemallCjDisputeAggregate();
        agg.setDisputeId(row.getId());
        agg.setOrderId(new LitemallOrderId(row.getOrderId()));
        agg.setUserId(new LitemallUserId(row.getUserId()));
        agg.setCjOrderId(row.getCjOrderId());
        agg.setBusinessDisputeId(row.getBusinessDisputeId());
        agg.setCjDisputeId(row.getCjDisputeId());
        agg.setReasonId(row.getReasonId());
        agg.setReasonName(row.getReasonName());
        agg.setExpectation(CjDisputeExpectation.fromCjCode(row.getExpectType()));
        agg.setMessage(row.getMessage());
        agg.setImageUrls(row.getImageUrls() == null || row.getImageUrls().isBlank()
                ? List.of() : Arrays.asList(row.getImageUrls().split(",")));
        agg.setCjStatus(row.getStatus());
        agg.setResolution(CjDisputeResolution.fromCjCode(row.getFinallyDeal()));
        agg.setRefundAmountUsd(row.getRefundAmount());
        agg.setResendOrderCode(row.getResendOrderCode());
        agg.setCancelled(Boolean.TRUE.equals(row.getCancelled()));
        agg.setAddTime(row.getAddTime());
        agg.setUpdateTime(row.getUpdateTime());
        return agg;
    }
}
