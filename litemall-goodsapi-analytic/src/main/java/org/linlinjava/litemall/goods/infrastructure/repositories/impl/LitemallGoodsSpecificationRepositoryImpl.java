package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsSpecificationAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsSpecificationRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsSpecificationId;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;


@Repository
public class LitemallGoodsSpecificationRepositoryImpl implements LitemallGoodsSpecificationRepository {

    @Resource
    private LitemallGoodsSpecificationMapper goodsSpecificationMapper;

    @Override
    public void add(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate) {
        LitemallGoodsSpecification goodsSpecification = convertToDataModel(goodsSpecificationAggregate);
        goodsSpecification.setAddTime(LocalDateTime.now());
        goodsSpecification.setUpdateTime(LocalDateTime.now());
        goodsSpecificationMapper.insertSelective(goodsSpecification);
    }

    @Override
    public int updateById(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate) {
        LitemallGoodsSpecification goodsSpecification = convertToDataModel(goodsSpecificationAggregate);
        goodsSpecification.setUpdateTime(LocalDateTime.now());
        return goodsSpecificationMapper.updateByPrimaryKeySelective(goodsSpecification);
    }

    @Override
    public void removeById(LitemallGoodsSpecificationId goodsSpecificationId) {
        LitemallGoodsSpecificationExample example = new LitemallGoodsSpecificationExample();
        example.or().andGoodsIdEqualTo(goodsSpecificationId.getId());
        goodsSpecificationMapper.logicalDeleteByExample(example);
    }

    @Override
    public void removeByGoodsId(LitemallGoodsId goodsId) {
        LitemallGoodsSpecificationExample example = new LitemallGoodsSpecificationExample();
        example.or().andGoodsIdEqualTo(goodsId.getId());
        goodsSpecificationMapper.logicalDeleteByExample(example);
    }

    @Override
    public LitemallGoodsSpecificationAggregate findById(LitemallGoodsSpecificationId goodsSpecificationId) {
        return convertToDomainModel(goodsSpecificationMapper.selectByPrimaryKey(goodsSpecificationId.getId()));
    }

    @Override
    public List<LitemallGoodsSpecificationAggregate> findSpecificationByGoodsId(LitemallGoodsId goodsId) {
        LitemallGoodsSpecificationExample example = new LitemallGoodsSpecificationExample();
        example.or().andGoodsIdEqualTo(goodsId.getId()).andDeletedEqualTo(false);
        return goodsSpecificationMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();

    }


    public LitemallGoodsSpecificationAggregate convertToDomainModel(LitemallGoodsSpecification record) {

        if(record == null){
            return null;
        }
        LitemallGoodsSpecificationAggregate goodsSpecificationAggregate = new LitemallGoodsSpecificationAggregate();

        // Relationship mappings
        goodsSpecificationAggregate.setGoodsSpecificationId(new LitemallGoodsSpecificationId(record.getId()));
        goodsSpecificationAggregate.setGoodsId(new LitemallGoodsId(record.getGoodsId()));

        goodsSpecificationAggregate.setSpecifications(record.getSpecification());
        goodsSpecificationAggregate.setValue(record.getValue());
        goodsSpecificationAggregate.setPicUrl(record.getPicUrl());


        goodsSpecificationAggregate.setAddTime(record.getAddTime());
        goodsSpecificationAggregate.setUpdateTime(record.getUpdateTime());
        goodsSpecificationAggregate.setDeleted(record.getDeleted());



        return goodsSpecificationAggregate;
    }

    public LitemallGoodsSpecification convertToDataModel(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate) {

        LitemallGoodsSpecification dataModel = new LitemallGoodsSpecification();

        if(goodsSpecificationAggregate.getGoodsSpecificationId() != null){
            dataModel.setId(goodsSpecificationAggregate.getGoodsSpecificationId().getId());
        }
        dataModel.setGoodsId(goodsSpecificationAggregate.getGoodsId().getId());

        dataModel.setSpecification(goodsSpecificationAggregate.getSpecifications());
        dataModel.setValue(goodsSpecificationAggregate.getValue());
        dataModel.setPicUrl(goodsSpecificationAggregate.getPicUrl());

        dataModel.setAddTime(goodsSpecificationAggregate.getAddTime());
        dataModel.setUpdateTime(goodsSpecificationAggregate.getUpdateTime());
        dataModel.setDeleted(goodsSpecificationAggregate.isDeleted());


        return dataModel;
    }
}
