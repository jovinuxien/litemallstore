package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallBrandExample;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallBrandAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallBrandRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LitemallBrandRepositoryImpl implements LitemallBrandRepository {

    private LitemallBrandMapper brandMapper;

    @Override
    public void add(LitemallBrandAggregate brandAggregate) {
        LitemallBrand brand = convertToDataModel(brandAggregate);
        brand.setAddTime(LocalDateTime.now());
        brand.setUpdateTime(LocalDateTime.now());
        brandMapper.insertSelective(brand);
    }

    @Override
    public int update(LitemallBrandAggregate brandAggregate) {
        LitemallBrand brand = convertToDataModel(brandAggregate);
        brand.setUpdateTime(LocalDateTime.now());
        return brandMapper.updateByPrimaryKeySelective(brand);
    }

    @Override
    public void removeById(LitemallBrandAggregate brandAggregate) {
        LitemallBrand brand = convertToDataModel(brandAggregate);
        brandMapper.logicalDeleteByPrimaryKey(brand.getId());
    }

    @Override
    public LitemallBrandAggregate findById(LitemallManufacturerId manufacturerId) {
        return convertToDomainModel(brandMapper.selectByPrimaryKey(manufacturerId.getId()));
    }

    @Override
    public List<LitemallBrandAggregate> findAll() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        return brandMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();

    }

    @Override
    public List<LitemallBrandAggregate> querySelective(String id, String name, Integer page, Integer size, String sort, String order) {
        LitemallBrandExample example = new LitemallBrandExample();
        LitemallBrandExample.Criteria criteria = example.createCriteria();

        if (!StringUtils.isEmpty(id)) {
            criteria.andIdEqualTo(Integer.valueOf(id));
        }
        if (!StringUtils.isEmpty(name)) {
            criteria.andNameLike("%" + name + "%");
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, size);
        return brandMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }


    public LitemallBrandAggregate convertToDomainModel(LitemallBrand record) {
        if(record == null){
            return null;
        }
        LitemallBrandAggregate brandAggregate = new LitemallBrandAggregate();

        // Relationship mappings
        brandAggregate.setBrandId(new LitemallManufacturerId(record.getId()));
        brandAggregate.setName(record.getName());
        brandAggregate.setDescription(record.getDesc());
        brandAggregate.setPicUrl(record.getPicUrl());
        brandAggregate.setSortOrder(record.getSortOrder().intValue());
        brandAggregate.setFloorPrice(record.getFloorPrice().doubleValue());

        brandAggregate.setAddTime(record.getAddTime());
        brandAggregate.setUpdateTime(record.getUpdateTime());
        brandAggregate.setDeleted(record.getDeleted());

        return brandAggregate;
    }

    public LitemallBrand convertToDataModel(LitemallBrandAggregate brandAggregate) {

        LitemallBrand dataModel = new LitemallBrand();

        if(brandAggregate.getBrandId() != null){
            dataModel.setId(brandAggregate.getBrandId().getId());
        }
        dataModel.setName(brandAggregate.getName());
        dataModel.setDesc(brandAggregate.getDescription());
        dataModel.setPicUrl(brandAggregate.getPicUrl());
        dataModel.setSortOrder(brandAggregate.getSortOrder().byteValue());
        dataModel.setFloorPrice(doubleToBigDecimal(brandAggregate.getFloorPrice()));


        dataModel.setAddTime(brandAggregate.getAddTime());
        dataModel.setUpdateTime(brandAggregate.getUpdateTime());
        dataModel.setDeleted(brandAggregate.isDeleted());

        return dataModel;
    }


    private BigDecimal doubleToBigDecimal(Double value) {
        return new BigDecimal(value);
    }
}
