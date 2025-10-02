package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallGrouponMapper;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.db.domain.LitemallGrouponExample;
import org.linlinjava.litemall.db.util.GrouponConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;


@Repository
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
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andIdEqualTo(id.getId()).andDeletedEqualTo(false);
        return (int) grouponMapper.countByExample(example);
    }

    @Override
    public int updateById(LitemallGrouponAggregate grouponAggregate) {
        return 0;
    }

    @Override
    public boolean hasJoin(LitemallUserId userId, LitemallGrouponId grouponId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andUserIdEqualTo(userId.getId()).andGrouponIdEqualTo(grouponId.getId()).andStatusNotEqualTo(GrouponConstant.STATUS_NONE).andDeletedEqualTo(false);
        return  grouponMapper.countByExample(example) != 0;
    }

    @Override
    public LitemallGrouponAggregate findById(LitemallGrouponId id) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andIdEqualTo(id.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(grouponMapper.selectOneByExample(example));
    }

    @Override
    public LitemallGrouponAggregate findByUserId(LitemallGrouponId id, LitemallUserId userId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andIdEqualTo(id.getId()).andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(grouponMapper.selectOneByExample(example));
    }

    @Override
    public List<LitemallGrouponAggregate> getMyGroupon(LitemallUserId userId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andUserIdEqualTo(userId.getId()).andCreatorUserIdEqualTo(userId.getId()).andGrouponIdEqualTo(0).andStatusNotEqualTo(LitemallGrouponStatus.STATUS_NONE.getCode()).andDeletedEqualTo(false);
        example.orderBy("add_time desc");
        return grouponMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public List<LitemallGrouponAggregate> getJoinRecord(LitemallGrouponId grouponId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andGrouponIdEqualTo(grouponId.getId()).andStatusNotEqualTo(LitemallGrouponStatus.STATUS_NONE.getCode()).andDeletedEqualTo(false);
        example.orderBy("add_time desc");
        return grouponMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public List<LitemallGrouponAggregate> getMyJoinGroupon(LitemallUserId userId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andUserIdEqualTo(userId.getId()).andGrouponIdNotEqualTo(0).andStatusNotEqualTo(LitemallGrouponStatus.STATUS_NONE.getCode()).andDeletedEqualTo(false);
        example.orderBy("add_time desc");
        return grouponMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public LitemallGrouponAggregate getGrouponByOrderId(LitemallOrderId orderId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andOrderIdEqualTo(orderId.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(grouponMapper.selectOneByExample(example));
    }


    @Override
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


    @Override
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
