package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallGrouponMapper;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;

import java.util.List;

public class LitemallGrouponRepositoryImpl implements LitemallGrouponRepository {

    private final LitemallGrouponMapper grouponMapper;


    public LitemallGrouponRepositoryImpl(LitemallGrouponMapper grouponMapper) {
        this.grouponMapper = grouponMapper;
    }


    @Override
    public LitemallGrouponId nextIdentity(LitemallGrouponId id) {
        return null;
    }

    @Override
    public void saveGroupon(LitemallGrouponAggregate grouponAggregate) {

    }

    @Override
    public int countGroupon(LitemallGrouponId grouponId) {
        return 0;
    }

    @Override
    public int countByGrouponId(LitemallGrouponId id) {
        return 0;
    }

    @Override
    public boolean existsByUserIdOrGrouponId(LitemallUserId userId, LitemallGrouponId grouponId) {
        return false;
    }

    @Override
    public LitemallGroupon findById(LitemallGrouponId id) {
        return null;
    }

    @Override
    public LitemallGroupon findByUserId(LitemallGrouponId id, LitemallUserId userId) {
        return null;
    }

    @Override
    public LitemallGroupon getMyGroupon(LitemallUserId userId) {
        return null;
    }

    @Override
    public List<LitemallGroupon> getJoinRecord(LitemallGrouponId grouponId) {
        return List.of();
    }

    @Override
    public LitemallGroupon getMyJoinGroupon(LitemallUserId userId) {
        return null;
    }

    @Override
    public LitemallGroupon getGrouponByOrderId(LitemallOrderId orderId) {
        return null;
    }


    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */
    public LitemallGroupon convertToDataModel(LitemallGrouponAggregate grouponAggregate) {
        LitemallGroupon dataModel = new LitemallGroupon();

        if(grouponAggregate.getGrouponId() != null){
            dataModel.setId(grouponAggregate.getGrouponId().getId());
        }
        dataModel.setUserId(grouponAggregate.getUserId().getId());
        dataModel.setOrderId(grouponAggregate.getOrderId().getId());

        dataModel.setStatus(grouponAggregate.getGrouponStatus().getCode());
        dataModel.setShareUrl(grouponAggregate.getShareUrl());


        //Other fields should be completed


        return dataModel;
    }


    public LitemallGrouponAggregate convertToDomainModel(LitemallGroupon record) {

        if(record == null){
            return null;
        }

        LitemallGrouponAggregate domainModel = new LitemallGrouponAggregate();

        // Relationship mappings
        domainModel.setGrouponId(new LitemallGrouponId(record.getId()));
        domainModel.setUserId(new LitemallUserId(record.getId()));
        domainModel.setOrderId(new LitemallOrderId(record.getId()));

        // Orther fields
        //domainModel.setGrouponStatus(record.getStatus());
        domainModel.setGrouponStatus(LitemallGrouponStatus.valueOf(record.getStatus().toString()));
        domainModel.setShareUrl(record.getShareUrl());
        domainModel.setCreatorUserTime(record.getAddTime());
        // Other fields should be completed

        return  domainModel;
    }
}
