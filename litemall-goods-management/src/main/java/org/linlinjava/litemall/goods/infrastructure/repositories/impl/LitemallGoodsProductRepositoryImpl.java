package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.GoodsProductMapper;

import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsProductExample;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsProductRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public class LitemallGoodsProductRepositoryImpl implements LitemallGoodsProductRepository {

    private final LitemallGoodsProductMapper goodsProductMapper;
    private final GoodsProductMapper goodsMapper;


    public LitemallGoodsProductRepositoryImpl(LitemallGoodsProductMapper goodsProductMapper, GoodsProductMapper goodsMapper) {
        this.goodsProductMapper = goodsProductMapper;
        this.goodsMapper = goodsMapper;
    }

    @Override
    public Optional<LitemallGoodsProductAggregate> findById(LitemallGoodsProductId goodsProductId) {
        return Optional.of(convertToDomainModel(goodsProductMapper.selectByPrimaryKey(Integer.valueOf(goodsProductId.getId()))));
    }

    @Override
    public List<LitemallGoodsProductAggregate> findByGoodsId(LitemallGoodsId goodsId) {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        example.or().andGoodsIdEqualTo(goodsId.getId()).andDeletedEqualTo(false);
        return goodsProductMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public void deleteById(LitemallGoodsProductId goodsProductId) {
        goodsProductMapper.logicalDeleteByPrimaryKey(Integer.valueOf(goodsProductId.getId()));
    }

    @Override
    public void deleteByGoodsId(LitemallGoodsId goodsId) {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        example.or().andGoodsIdEqualTo(goodsId.getId());
        goodsProductMapper.logicalDeleteByExample(example);
    }

    @Override
    public void addGoodsProduct(LitemallGoodsProductAggregate productAggregate) {
        LitemallGoodsProduct goodsProduct = convertToDataModel(productAggregate);
        goodsProduct.setAddTime(LocalDateTime.now());
        goodsProduct.setUpdateTime(LocalDateTime.now());
        goodsProductMapper.insertSelective(goodsProduct);
    }

    @Override
    public void updateGoodsById(LitemallGoodsProductAggregate goodsProductAggregate) {
        LitemallGoodsProduct product = convertToDataModel(goodsProductAggregate);
        product.setUpdateTime(LocalDateTime.now());
        goodsProductMapper.updateByPrimaryKeySelective(product);
    }

    @Override
    public int count() {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        example.or().andDeletedEqualTo(false);
        return (int) goodsProductMapper.countByExample(example);
    }

    @Override
    public boolean isStockEnough(LitemallGoodsProductId productId, Short number) {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        example.or().andIdEqualTo(Integer.valueOf(productId.getId())).andNumberGreaterThanOrEqualTo(number.intValue()).andDeletedEqualTo(false);
        return !goodsProductMapper.selectByExample(example).isEmpty();
    }

    @Override
    public int reduceStock(LitemallGoodsProductId productId, Short number) {
        return goodsMapper.reduceStock(Integer.valueOf(productId.getId()), number);
    }

    @Override
    public int addStock(LitemallGoodsProductId productId, Short number) {
        return goodsMapper.addStock(Integer.valueOf(productId.getId()), number);

    }

    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */
    public LitemallGoodsProduct convertToDataModel(LitemallGoodsProductAggregate goodsProductAggregate) {
        LitemallGoodsProduct dataModel = new LitemallGoodsProduct();

        if(goodsProductAggregate.getGoodsProductId() != null){
            dataModel.setId(Integer.parseInt(goodsProductAggregate.getGoodsProductId().getId()));
        }
        dataModel.setGoodsId(Integer.parseInt(goodsProductAggregate.getGoodsProductId().getId()));
        dataModel.setSpecifications(goodsProductAggregate.getSpecifications());

        dataModel.setPrice(goodsProductAggregate.getPrice().getAmount());
        dataModel.setNumber(goodsProductAggregate.getNumber());
        dataModel.setUrl(goodsProductAggregate.getUrl());
        dataModel.setUpdateTime(goodsProductAggregate.getUpdateTime());
        dataModel.setAddTime(goodsProductAggregate.getAddTime());
        dataModel.setDeleted(goodsProductAggregate.isDeleted());


        //Other fields should be completed

        return dataModel;
    }


    public LitemallGoodsProductAggregate convertToDomainModel(LitemallGoodsProduct record) {

        if(record == null){
            return null;
        }

        LitemallGoodsProductAggregate domainModel = new LitemallGoodsProductAggregate();

        // Relationship mappings
        domainModel.setGoodsProductId(new LitemallGoodsProductId(record.getId().toString()));
        domainModel.setGoodsId(new LitemallGoodsId(record.getId()));

        // Orther fields
        //domainModel.setGrouponStatus(record.getStatus());
        domainModel.setSpecifications(record.getSpecifications());
        domainModel.setPrice(new LitemallMoney(record.getPrice()));
        domainModel.setNumber(record.getNumber());
        domainModel.setUrl(record.getUrl());

        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setAddTime(record.getAddTime());
        domainModel.setDeleted(record.getDeleted());
        // Other fields should be completed

        return  domainModel;
    }
}
