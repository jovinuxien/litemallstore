package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallGrouponMapper;
import org.linlinjava.litemall.db.dao.LitemallGrouponRulesMapper;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.db.domain.LitemallGrouponExample;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.db.util.GrouponConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;


@Repository
public class LitemallGrouponRepositoryImpl implements LitemallGrouponRepository {

    private final LitemallGrouponMapper grouponMapper;

    @Autowired
    private final LitemallGrouponRulesRepository rulesRepository;

    public LitemallGrouponRepositoryImpl(LitemallGrouponMapper grouponMapper, LitemallGrouponRulesMapper grouponRulesMapper, LitemallGrouponRulesRepository grouponRulesMapper1) {
        this.grouponMapper = grouponMapper;
        this.rulesRepository = grouponRulesMapper1;
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
    public boolean isGrouponCreator(LitemallUserId userId, LitemallGrouponId grouponId) {
        LitemallGrouponAggregate grouponAggregate = this.findById(grouponId);


        if (grouponAggregate == null || grouponAggregate.isDeleted()) {
            return false;
        }
        // Le créateur est stocké dans creatorUserId
        return grouponAggregate.getCreatorUserId().getId().equals(userId.getId());
    }

    @Override
    public LitemallGrouponAggregate findById(LitemallGrouponId id) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andIdEqualTo(id.getId()).andDeletedEqualTo(false);
        return convertToAggregate(grouponMapper.selectOneByExample(example));
    }

    @Override
    public LitemallGrouponAggregate findByUserId(LitemallGrouponId id, LitemallUserId userId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andIdEqualTo(id.getId()).andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return convertToAggregate(grouponMapper.selectOneByExample(example));
    }

    @Override
    public List<LitemallGrouponAggregate> getMyGroupon(LitemallUserId userId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andUserIdEqualTo(userId.getId()).andCreatorUserIdEqualTo(userId.getId()).andGrouponIdEqualTo(0).andStatusNotEqualTo(LitemallGrouponStatus.STATUS_NONE.getCode()).andDeletedEqualTo(false);
        example.orderBy("add_time desc");
        return grouponMapper.selectByExample(example).stream().map(this::convertToAggregate).collect(Collectors.toList());
    }

    @Override
    public List<LitemallGrouponAggregate> getJoinRecord(LitemallGrouponId grouponId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andGrouponIdEqualTo(grouponId.getId()).andStatusNotEqualTo(LitemallGrouponStatus.STATUS_NONE.getCode()).andDeletedEqualTo(false);
        example.orderBy("add_time desc");
        return grouponMapper.selectByExample(example).stream().map(this::convertToAggregate).collect(Collectors.toList());
    }

    @Override
    public List<LitemallGrouponAggregate> getMyJoinGroupon(LitemallUserId userId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andUserIdEqualTo(userId.getId()).andGrouponIdNotEqualTo(0).andStatusNotEqualTo(LitemallGrouponStatus.STATUS_NONE.getCode()).andDeletedEqualTo(false);
        example.orderBy("add_time desc");
        return grouponMapper.selectByExample(example).stream().map(this::convertToAggregate).collect(Collectors.toList());
    }

    @Override
    public LitemallGrouponAggregate getGrouponByOrderId(LitemallOrderId orderId) {
        LitemallGrouponExample example = new LitemallGrouponExample();
        example.or().andOrderIdEqualTo(orderId.getId()).andDeletedEqualTo(false);
        return convertToAggregate(grouponMapper.selectOneByExample(example));
    }

    @Override
    public int countUserActiveParticipationsInRules(LitemallUserId userId, LitemallGrouponRulesId rulesId) {
        List<LitemallGrouponAggregate> userGroupons = this.getMyJoinGroupon(userId);

        return (int) userGroupons.stream()
                .filter(groupon -> {
                    // Vérifier la correspondance des règles et le statut
                    boolean basicMatch = groupon.getGrouponRulesId().getId().equals(rulesId.getId()) &&
                            !groupon.getGrouponStatus().getCode().equals(GrouponConstant.STATUS_NONE) &&
                            !groupon.isDeleted();

                    // Vérifier que les règles sont toujours actives
                    if (basicMatch) {
                        LitemallGrouponRulesAggregate rules = this.rulesRepository.findById(rulesId);
                        if (rules != null && rules.getStatus().equals(GrouponConstant.RULE_STATUS_ON) && !rules.isDeleted()) {
                            this.rulesRepository.isExpired(rules);
                        }
                        return false;
                    }
                    return false;
                })
                .count();

    }


    @Override
    public LitemallGroupon convertToDataModel(LitemallGrouponAggregate grouponAggregate) {
        LitemallGroupon dataModel = new LitemallGroupon();

        if(grouponAggregate.getGrouponId() != null){
            dataModel.setId(grouponAggregate.getGrouponId().getId());
        }
        dataModel.setUserId(grouponAggregate.getCreatorUserId().getId());
        dataModel.setOrderId(grouponAggregate.getOrderId().getId());
        dataModel.setRulesId(grouponAggregate.getGrouponRulesId().getId());



        dataModel.setStatus(grouponAggregate.getGrouponStatus().getCode());
        dataModel.setShareUrl(grouponAggregate.getShareUrl());


        dataModel.setCreatorUserTime(grouponAggregate.getCreatorUserTime());
        dataModel.setAddTime(grouponAggregate.getAddTime());
        dataModel.setUpdateTime(grouponAggregate.getUpdateTime());
        dataModel.setDeleted(grouponAggregate.isDeleted());

        //Other fields should be completed
        return dataModel;
    }


    @Override
    public LitemallGrouponAggregate convertToAggregate(LitemallGroupon groupon) {

        if(groupon == null){
            return null;
        }
        return LitemallGrouponAggregate.builder()
                .grouponId(new LitemallGrouponId(groupon.getId()))
                .orderId(new LitemallOrderId(groupon.getOrderId()))
                .creatorUserId(new LitemallUserId(groupon.getCreatorUserId()))
                .grouponRulesId(new LitemallGrouponRulesId(groupon.getRulesId()))

                //.(new LitemallUserId(groupon.getUserId()))
                .shareUrl(groupon.getShareUrl())
                .grouponStatus(mapToDomainStatus(groupon.getStatus()))

                .creatorUserTime(groupon.getCreatorUserTime())
                .addTime(groupon.getAddTime())
                .updateTime(groupon.getUpdateTime())
                .deleted(groupon.getDeleted())
                //.rules(mapToRulesAggregate(rules))
                .build();
    }

    @Override
    public List<LitemallGrouponAggregate> findActiveParticipationsByUserAndRules(LitemallUserId userId, LitemallGrouponRulesId rulesId) {
        // Get all the user's groupons'
        List<LitemallGrouponAggregate> userGroupons = this.getMyJoinGroupon(userId);

        return userGroupons.stream()
                .filter(groupon -> isActiveParticipation(groupon, rulesId))
                //.map(this::convertToAggregate)
                .collect(Collectors.toList());
    }


    private boolean isActiveParticipation(LitemallGrouponAggregate groupon, LitemallGrouponRulesId rulesId) {
        // Vérifier si le groupon correspond aux règles et est actif
        boolean matchesRules = groupon.getGrouponRulesId().getId().equals(rulesId.getId());
        boolean isActive = !groupon.getGrouponStatus().getCode().equals(GrouponConstant.STATUS_NONE) &&
                !groupon.isDeleted();

        // Vérifier si les règles de groupon sont encore actives
        LitemallGrouponRulesAggregate rules = this.rulesRepository.findById(rulesId);
        boolean rulesActive = rules != null &&
                rules.getStatus().equals(GrouponConstant.RULE_STATUS_ON) &&
                !rules.isDeleted() &&
                !this.rulesRepository.isExpired(rules);

        return matchesRules && isActive && rulesActive;
    }


    private LitemallGrouponStatus mapToDomainStatus(Short status) {

        int statusInt = status.intValue();
        switch (statusInt) {
            case 1: return LitemallGrouponStatus.STATUS_ON;
            case 2: return LitemallGrouponStatus.STATUS_SUCCEED;
            case 3: return LitemallGrouponStatus.STATUS_FAIL;
            default: return LitemallGrouponStatus.STATUS_NONE;
        }
    }
}
