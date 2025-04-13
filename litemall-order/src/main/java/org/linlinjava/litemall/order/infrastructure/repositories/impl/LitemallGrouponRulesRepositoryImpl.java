package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.dao.LitemallGrouponRulesMapper;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRulesRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Repository
public class LitemallGrouponRulesRepositoryImpl implements LitemallGrouponRulesRepository {

    private final LitemallGoodsMapper goodsMapper;
    private final LitemallGrouponRulesMapper grouponRulesMapper;

    private LitemallGoods.Column[] goodsColumns =
            new LitemallGoods.Column[]{LitemallGoods.Column.id, LitemallGoods.Column.name, LitemallGoods.Column.brief, LitemallGoods.Column.picUrl, LitemallGoods.Column.counterPrice, LitemallGoods.Column.retailPrice};

    public LitemallGrouponRulesRepositoryImpl(LitemallGoodsMapper goodsMapper, LitemallGrouponRulesMapper grouponRulesMapper) {
        this.goodsMapper = goodsMapper;
        this.grouponRulesMapper = grouponRulesMapper;
    }

    @Override
    public LitemallGrouponRulesAggregate findById(LitemallGrouponRulesId id) {
        LitemallGrouponRules grouponRules = grouponRulesMapper.selectByPrimaryKey(id.getId());
        return convertToDomainModel(grouponRules);
    }

    @Override
    public int createGrouponRules(LitemallGrouponRulesAggregate grouponRulesAggregate) {
        LitemallGrouponRules rules = convertToDataModel(grouponRulesAggregate);
        rules.setAddTime(LocalDateTime.now());
        rules.setUpdateTime(LocalDateTime.now());
       return grouponRulesMapper.insertSelective(rules);
    }

    @Override
    public int countByGoodsId(LitemallGrouponRulesId goodsId) {
        LitemallGrouponRulesExample example = new LitemallGrouponRulesExample();
        example.or().andGoodsIdEqualTo(goodsId.getId()).andStatusEqualTo(LitemallGrouponStatus.RULE_STATUS_ON.getCode());
        return (int) grouponRulesMapper.countByExample(example);

    }

    @Override
    public void deleteGrouponRulesById(LitemallGrouponRulesId rulesId) {
        grouponRulesMapper.logicalDeleteByPrimaryKey(rulesId.getId());
    }

    @Override
    public void updateGrouponRules(LitemallGrouponRulesAggregate grouponRulesAggregate) {
        LitemallGrouponRules rules = convertToDataModel(grouponRulesAggregate);
        grouponRulesMapper.updateByPrimaryKey(rules);

    }



    @Override
    public LitemallGrouponRulesAggregate findGrouponRulesByGoodsId(LitemallGrouponRulesId goodsId) {
         LitemallGrouponRulesExample example = new LitemallGrouponRulesExample();
         example.or().andGoodsIdEqualTo(goodsId.getId()).andStatusEqualTo(LitemallGrouponStatus.RULE_STATUS_ON.getCode());
        return convertToDomainModel(grouponRulesMapper.selectOneByExample(example));
    }

    @Override
    public List<LitemallGrouponRulesAggregate> getAllGrouponList(Integer page, Integer size, String sort, String order) {
        return queryList(null, LitemallGrouponStatus.STATUS_NONE, null, size, sort, order);
    }

    @Override
    public List<LitemallGrouponRulesAggregate> findAllGrouponRulesList(LitemallGoodsId goodsId, LitemallGrouponStatus status, Integer page, Integer size, String sort, String order) {
        return queryList(goodsId, status, page, size, sort, order);
    }

    @Override
    public List<LitemallGrouponRulesAggregate> getGrouponByStatus(LitemallGrouponStatus status) {
        return queryList(null, status, null, null, "add_time", "desc");
    }


    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */

    private List<LitemallGrouponRulesAggregate> queryList(LitemallGoodsId goodsId, LitemallGrouponStatus status,  Integer page, Integer size, String sort, String order) {

        LitemallGrouponRulesExample example = new LitemallGrouponRulesExample();
        LitemallGrouponRulesExample.Criteria criteria = example.createCriteria();


        if (goodsId != null) {
            criteria.andGoodsIdEqualTo(goodsId.getId());
        }
        if (status!= null) {
            criteria.andStatusEqualTo(status.getCode());
        }

        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        if (!StringUtils.isEmpty(page) && !StringUtils.isEmpty(size)) {
            PageHelper.startPage(page, size);
        }

        return grouponRulesMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }
    public LitemallGrouponRules convertToDataModel(LitemallGrouponRulesAggregate grouponRulesAggregate) {

        LitemallGrouponRules dataModel = new LitemallGrouponRules();

        if(grouponRulesAggregate.getGrouponRulesId().getId() != null){
            dataModel.setId(grouponRulesAggregate.getGrouponRulesId().getId());
        }
        dataModel.setGoodsId(grouponRulesAggregate.getGoodsId().getId());
        dataModel.setPicUrl(grouponRulesAggregate.getPicUrl());

        dataModel.setStatus(grouponRulesAggregate.getStatus().getCode());
        dataModel.setDiscount(grouponRulesAggregate.getDiscount());
        dataModel.setDiscountMember(grouponRulesAggregate.getDiscountMember());
        dataModel.setExpireTime(grouponRulesAggregate.getExpireTime());
        dataModel.setAddTime(grouponRulesAggregate.getAddTime());
        dataModel.setUpdateTime(grouponRulesAggregate.getUpdateTime());



        //Other fields should be completed

        return dataModel;
    }


    public LitemallGrouponRulesAggregate convertToDomainModel(LitemallGrouponRules record) {

        LitemallGrouponRulesAggregate domainModel = new LitemallGrouponRulesAggregate();
        if(record == null){
            return null;
        }
        // Relationship mappings
        domainModel.setGrouponRulesId(new LitemallGrouponRulesId(record.getId()));
        domainModel.setGoodsId(new LitemallGoodsId(record.getGoodsId()));

        domainModel.setGoodsName(record.getGoodsName());
        domainModel.setPicUrl(record.getPicUrl());
        domainModel.setDiscount(record.getDiscount());
        domainModel.setDiscountMember(record.getDiscountMember());

        domainModel.setStatus(LitemallGrouponStatus.valueOf(record.getStatus().toString()));
        domainModel.setExpireTime(record.getExpireTime());
        domainModel.setAddTime(record.getAddTime());
        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setDeleted(record.getDeleted());

        // Orther fields
        return  domainModel;
    }




}
