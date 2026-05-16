package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallCatalogRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LitemallCatalogRepositoryImpl implements LitemallCatalogRepository {

    @Autowired
    private LitemallCategoryMapper categoryMapper;
    private LitemallCategory.Column[] CHANNEL = {LitemallCategory.Column.id, LitemallCategory.Column.name, LitemallCategory.Column.iconUrl};

    @Override
    public void save(LitemallCategoryAggregate categoryAggregate) {
        LitemallCategory category = convertToDataModel(categoryAggregate);
        category.setAddTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.insertSelective(category);
    }

    @Override
    public int updateById(LitemallCategoryAggregate categoryAggregate) {
        LitemallCategory category = convertToDataModel(categoryAggregate);
        category.setUpdateTime(LocalDateTime.now());
        return categoryMapper.updateByPrimaryKeySelective(category);
    }

    @Override
    public void removeById(LitemallCategoryId id) {
        categoryMapper.logicalDeleteByPrimaryKey(Integer.valueOf(id.getId()));
    }

    @Override
    public List<LitemallCategoryAggregate> queryL1(Integer offset, Integer limit) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andDeletedEqualTo(false);
        PageHelper.startPage(offset, limit);
        return categoryMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallCategoryAggregate> queryByPid(Integer parentId) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andPidEqualTo(parentId).andDeletedEqualTo(false);
        return categoryMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallCategoryAggregate> queryL2ByIds(List<Integer> ids) {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andIdIn(ids).andLevelEqualTo("L2").andDeletedEqualTo(false);
        return categoryMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
        //return List.of();
    }

    @Override
    public LitemallCategoryAggregate findById(LitemallCategoryId id) {
        return convertToDomainModel(categoryMapper.selectByPrimaryKey(Integer.valueOf(id.getId())));
    }

    @Override
    public List<LitemallCategoryAggregate> querySelective(String id, String name, Integer page, Integer size, String sort, String order) {

        LitemallCategoryExample example = new LitemallCategoryExample();
        LitemallCategoryExample.Criteria criteria = example.createCriteria();

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
        return categoryMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallCategoryAggregate> queryChannel() {
        LitemallCategoryExample example = new LitemallCategoryExample();
        example.or().andLevelEqualTo("L1").andDeletedEqualTo(false);
        return categoryMapper.selectByExampleSelective(example, CHANNEL).stream().map(this::convertToDomainModel).toList();
        //return List.of();
    }


    public LitemallCategoryAggregate convertToDomainModel(LitemallCategory record) {

        if(record == null){
            return null;
        }
        LitemallCategoryAggregate categoryAggregate = new LitemallCategoryAggregate();

        // Relationship mappings
        categoryAggregate.setCategoryId(new LitemallCategoryId(record.getId()));
        categoryAggregate.setCategoryName(record.getName());
        categoryAggregate.setKeywords(record.getKeywords());
        categoryAggregate.setDescription(record.getDesc());

        if(record.getPid() != null){
            categoryAggregate.setParentId(record.getId());
        }


        categoryAggregate.setIconUrl(record.getIconUrl());
        categoryAggregate.setPicUrl(record.getPicUrl());
        categoryAggregate.setLevel(record.getLevel());

        //categoryAggregate.setSortOrder(record.getSortOrder().byteValue());

        categoryAggregate.setAddTime(record.getAddTime());
        categoryAggregate.setUpdateTime(record.getUpdateTime());
        categoryAggregate.setDeleted(record.getDeleted());

        return categoryAggregate;
    }

    public LitemallCategory convertToDataModel(LitemallCategoryAggregate categoryAggregate) {

        LitemallCategory dataModel = new LitemallCategory();

        if(categoryAggregate.getCategoryId() != null){
            dataModel.setId(Integer.valueOf(categoryAggregate.getCategoryId().getId()));
        }
        dataModel.setName(categoryAggregate.getCategoryName());
        dataModel.setKeywords(categoryAggregate.getKeywords());
        dataModel.setDesc(categoryAggregate.getDescription());
        dataModel.setPid(categoryAggregate.getParentId());

        dataModel.setIconUrl(categoryAggregate.getIconUrl());
        dataModel.setPid(categoryAggregate.getParentId());
        dataModel.setLevel(categoryAggregate.getLevel());

        //dataModel.setSortOrder(categoryAggregate.getSortOrder());


        dataModel.setAddTime(categoryAggregate.getAddTime());
        dataModel.setUpdateTime(categoryAggregate.getUpdateTime());
        dataModel.setDeleted(categoryAggregate.isDeleted());

        return dataModel;
    }
}
