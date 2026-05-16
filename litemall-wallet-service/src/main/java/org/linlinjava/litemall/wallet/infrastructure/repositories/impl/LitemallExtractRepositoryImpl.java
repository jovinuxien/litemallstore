package org.linlinjava.litemall.wallet.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserExtractMapper;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.wallet.domain.model.repositories.LitemallExtractRepository;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.enums.LitemallExtractStatus;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet.LitemallExtractId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class LitemallExtractRepositoryImpl implements LitemallExtractRepository {

    private final LitemallUserExtractMapper extractMapper;

    public LitemallExtractRepositoryImpl(LitemallUserExtractMapper extractMapper) {
        this.extractMapper = extractMapper;
    }

    @Override
    public void add(LitemallExtractAggregate extract) {
        LitemallUserExtract record = toDataModel(extract);
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        record.setStatus((byte) LitemallExtractStatus.PENDING.getCode());
        extractMapper.insertSelective(record);
        extract.setExtractId(new LitemallExtractId(record.getId()));
    }

    @Override
    public Optional<LitemallExtractAggregate> findById(LitemallExtractId extractId) {
        LitemallUserExtract record = extractMapper.selectByPrimaryKey(extractId.getId());
        return record != null ? Optional.of(toDomainModel(record)) : Optional.empty();
    }

    @Override
    public List<LitemallExtractAggregate> findByUserId(LitemallUserId userId) {
        return extractMapper.selectByUserId(userId.getId()).stream()
                .map(this::toDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public int updateStatus(LitemallExtractId extractId, LitemallExtractStatus status, String failMsg) {
        LitemallUserExtract record = new LitemallUserExtract();
        record.setId(extractId.getId());
        record.setStatus((byte) status.getCode());
        record.setFailMsg(failMsg);
        if (status == LitemallExtractStatus.REJECTED) {
            record.setFailTime(LocalDateTime.now());
        }
        return extractMapper.updateByPrimaryKeySelective(record);
    }

    private LitemallExtractAggregate toDomainModel(LitemallUserExtract r) {
        LitemallExtractAggregate agg = new LitemallExtractAggregate();
        agg.setExtractId(new LitemallExtractId(r.getId()));
        agg.setUserId(new LitemallUserId(r.getUserId()));
        agg.setRealName(r.getRealName());
        agg.setExtractType(r.getExtractType());
        agg.setBankCode(r.getBankCode());
        agg.setBankAddress(r.getBankAddress());
        agg.setExtractAmount(new LitemallMoney(r.getExtractPrice()));
        agg.setBalanceAfter(r.getBalance() != null ? new LitemallMoney(r.getBalance()) : null);
        agg.setStatus(r.getStatus() != null ? LitemallExtractStatus.fromCode(r.getStatus()) : LitemallExtractStatus.PENDING);
        agg.setFailMsg(r.getFailMsg());
        agg.setAddTime(r.getAddTime());
        agg.setUpdateTime(r.getUpdateTime());
        return agg;
    }

    private LitemallUserExtract toDataModel(LitemallExtractAggregate agg) {
        LitemallUserExtract r = new LitemallUserExtract();
        if (agg.getExtractId() != null) r.setId(agg.getExtractId().getId());
        r.setUserId(agg.getUserId().getId());
        r.setRealName(agg.getRealName());
        r.setExtractType(agg.getExtractType());
        r.setBankCode(agg.getBankCode());
        r.setBankAddress(agg.getBankAddress());
        r.setExtractPrice(agg.getExtractAmount().getAmount());
        r.setBalance(agg.getBalanceAfter() != null ? agg.getBalanceAfter().getAmount() : null);
        r.setStatus(agg.getStatus() != null ? (byte) agg.getStatus().getCode() : (byte) 0);
        r.setFailMsg(agg.getFailMsg());
        return r;
    }
}