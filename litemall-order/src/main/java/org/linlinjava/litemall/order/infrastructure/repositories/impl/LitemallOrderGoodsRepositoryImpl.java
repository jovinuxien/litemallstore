package org.linlinjava.litemall.order.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallOrderGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallOrderGoods;
import org.linlinjava.litemall.db.domain.LitemallOrderGoodsExample;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public class LitemallOrderGoodsRepositoryImpl implements LitemallOrderGoodsRepository {

    private final LitemallOrderGoodsMapper orderGoodsMapper;

    public LitemallOrderGoodsRepositoryImpl(LitemallOrderGoodsMapper orderGoodsMapper){
        this.orderGoodsMapper = orderGoodsMapper;
    }

    @Override
    public void add(LitemallOrderGoodsAggregate orderGoodsAggregate) {
       LitemallOrderGoods orderGoods = convertToDataModel(orderGoodsAggregate);
        orderGoods.setAddTime(LocalDateTime.now());
        orderGoods.setUpdateTime(LocalDateTime.now());
        orderGoodsMapper.insertSelective(orderGoods);
    }

    @Override
    public LitemallOrderGoodsAggregate findById(LitemallOrderGoodsId orderGoodsId) {
        return convertToDomainModel(orderGoodsMapper.selectByPrimaryKey(orderGoodsId.getId()));

    }

    @Override
    public List<LitemallOrderGoodsAggregate> findByOId(LitemallOrderId orderId) {
        LitemallOrderGoodsExample example = new LitemallOrderGoodsExample();
        example.or().andOrderIdEqualTo(orderId.getId()).andDeletedEqualTo(false);
        return orderGoodsMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public void updateById(LitemallOrderGoodsAggregate orderGoodsAggregate) {
         LitemallOrderGoods orderGoods = convertToDataModel(orderGoodsAggregate);
        orderGoods.setUpdateTime(LocalDateTime.now());
        orderGoodsMapper.updateByPrimaryKeySelective(orderGoods);
    }

    @Override
    public Short getComments(LitemallOrderId orderId) {
        LitemallOrderGoodsExample example = new LitemallOrderGoodsExample();
        example.or().andOrderIdEqualTo(orderId.getId()).andDeletedEqualTo(false);
        long count = orderGoodsMapper.countByExample(example);
        return (short) count;
    }

    @Override
    public void deleteByOrderId(LitemallOrderId orderId) {
        LitemallOrderGoodsExample example = new LitemallOrderGoodsExample();
        example.or().andOrderIdEqualTo(orderId.getId()).andDeletedEqualTo(false);
        orderGoodsMapper.logicalDeleteByExample(example);
    }

    @Override
    public boolean checkExist(LitemallGoodsId goodsId) {
        LitemallOrderGoodsExample example = new LitemallOrderGoodsExample();
        example.or().andGoodsIdEqualTo(goodsId.getId()).andDeletedEqualTo(false);
        return orderGoodsMapper.countByExample(example) != 0;
    }

    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */
    public LitemallOrderGoods convertToDataModel(LitemallOrderGoodsAggregate orderGoodsAggregate) {
        LitemallOrderGoods dataModel = new LitemallOrderGoods();

        if(orderGoodsAggregate.getOrderGoodsId() != null){
            dataModel.setId(orderGoodsAggregate.getOrderGoodsId());
        }
        dataModel.setOrderId(orderGoodsAggregate.getGoodsId().getId());
        dataModel.setGoodsId(orderGoodsAggregate.getGoodsId().getId());
        dataModel.setProductId(orderGoodsAggregate.getProductId().getId());


        dataModel.setGoodsSn(orderGoodsAggregate.getGoodsSn());
        dataModel.setGoodsName(orderGoodsAggregate.getGoodsName());
        dataModel.setNumber(orderGoodsAggregate.getNumber());
        dataModel.setPrice(orderGoodsAggregate.getPrice().getAmount());
        dataModel.setPicUrl(orderGoodsAggregate.getPicUrl());
        dataModel.setComment(Integer.valueOf(orderGoodsAggregate.getComment()));

        dataModel.setAddTime(orderGoodsAggregate.getAddTime());
        dataModel.setUpdateTime(orderGoodsAggregate.getUpdateTime());
        dataModel.setDeleted(orderGoodsAggregate.isDelete());

        //Other fields should be completed

        return dataModel;
    }


    public LitemallOrderGoodsAggregate convertToDomainModel(LitemallOrderGoods record) {

        if(record == null){
            return null;
        }

        LitemallOrderGoodsAggregate domainModel = new LitemallOrderGoodsAggregate();

        // Relationship mappings
        domainModel.setOrderId(new LitemallOrderId(record.getId()));
        domainModel.setGoodsId(new LitemallGoodsId(record.getId()));
        domainModel.setProductId(new LitemallGoodsProductId(record.getId()));


        domainModel.setGoodsSn(record.getGoodsSn());
        domainModel.setGoodsName(record.getGoodsName());
        domainModel.setPicUrl(record.getPicUrl());
        domainModel.setPrice(new LitemallMoney(record.getPrice()));
        domainModel.setNumber(record.getNumber());

        domainModel.setSpecifications(record.getSpecifications());
        domainModel.setComment(record.getComment().toString());


        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setAddTime(record.getAddTime());
        domainModel.setDelete(record.getDeleted());
        // Other fields should be completed

        return  domainModel;
    }


}
