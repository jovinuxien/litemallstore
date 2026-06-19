package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallUserBillMapper;
import org.linlinjava.litemall.db.domain.LitemallUserBill;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallBillRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallBillDirection;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallBillId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallBillRepositoryImpl implements LitemallBillRepository {

    private final LitemallUserBillMapper billMapper;

    public LitemallBillRepositoryImpl(LitemallUserBillMapper billMapper) {
        this.billMapper = billMapper;
    }

    @Override
    public void add(LitemallBillAggregate bill) {
        LitemallUserBill record = toDataModel(bill);
        record.setAddTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setDeleted(false);
        record.setStatus((byte) 1);
        billMapper.insertSelective(record);
    }

    @Override
    public List<LitemallBillAggregate> findByUserId(LitemallUserId userId) {
        return billMapper.selectByUserId(userId.getId()).stream()
                .map(this::toDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public LitemallBillAggregate findById(LitemallBillId billId) {
        LitemallUserBill record = billMapper.selectByPrimaryKey(billId.getId());
        return record != null ? toDomainModel(record) : null;
    }

    private LitemallBillAggregate toDomainModel(LitemallUserBill r) {
        LitemallBillAggregate agg = new LitemallBillAggregate();
        agg.setBillId(new LitemallBillId(r.getId()));
        agg.setUserId(new LitemallUserId(r.getUserId()));
        agg.setLinkId(r.getLinkId());
        agg.setDirection(r.getPm() != null && r.getPm() == 1 ? LitemallBillDirection.CREDIT : LitemallBillDirection.DEBIT);
        agg.setTitle(r.getTitle());
        agg.setCategory(r.getCategory());
        agg.setType(r.getType());
        agg.setAmount(new LitemallMoney(r.getNumber()));
        agg.setBalanceAfter(new LitemallMoney(r.getBalance()));
        agg.setMark(r.getMark());
        agg.setStatus(r.getStatus());
        agg.setAddTime(r.getAddTime());
        agg.setUpdateTime(r.getUpdateTime());
        return agg;
    }

    private LitemallUserBill toDataModel(LitemallBillAggregate agg) {
        LitemallUserBill r = new LitemallUserBill();
        if (agg.getBillId() != null) r.setId(agg.getBillId().getId());
        r.setUserId(agg.getUserId().getId());
        r.setLinkId(agg.getLinkId());
        r.setPm(agg.getDirection() == LitemallBillDirection.CREDIT ? (byte) 1 : (byte) 0);
        r.setTitle(agg.getTitle());
        r.setCategory(agg.getCategory());
        r.setType(agg.getType());
        r.setNumber(agg.getAmount().getAmount());
        r.setBalance(agg.getBalanceAfter().getAmount());
        r.setMark(agg.getMark());
        return r;
    }
}
