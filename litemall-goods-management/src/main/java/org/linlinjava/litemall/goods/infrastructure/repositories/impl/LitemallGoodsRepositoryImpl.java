package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsExample;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallGoodsRepositoryImpl implements LitemallGoodsRepository {

    LitemallGoods.Column[] columns = new LitemallGoods.Column[]{LitemallGoods.Column.id, LitemallGoods.Column.name, LitemallGoods.Column.brief, LitemallGoods.Column.picUrl, LitemallGoods.Column.isHot, LitemallGoods.Column.isNew, LitemallGoods.Column.counterPrice, LitemallGoods.Column.retailPrice};
    private final LitemallGoodsMapper goodsMapper;

    public LitemallGoodsRepositoryImpl(LitemallGoodsMapper goodsMapper){
        this.goodsMapper = goodsMapper;
    }


    @Override
    public void addGoods(LitemallGoodsAggregate goodsAggregate) {

    }

    @Override
    public int count() {
        return 0;
    }

    @Override
    public int updateById(LitemallGoodsAggregate goodsAggregate) {
        return 0;
    }

    @Override
    public void deleteById(LitemallGoodsId goodsId) {

    }

    @Override
    public LitemallGoodsAggregate findById(LitemallGoodsId goodsId) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andIdEqualTo(goodsId.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(goodsMapper.selectOneByExampleWithBLOBs(example));
    }

    @Override
    public int queryOnSale() {
        return 0;
    }

    @Override
    public List<LitemallGoodsAggregate> queryByIds(List<LitemallGoodsId> ids) {
        LitemallGoodsExample example = new LitemallGoodsExample();

        List<Integer> integerList = new ArrayList<>(0);
        for(LitemallGoodsId id : ids){
            integerList.add(id.getId());
        }

        example.or().andIdIn(integerList).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        return goodsMapper.selectByExampleSelective(example, columns).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallGoodsAggregate> queryByHot(int offset, int limit) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andIsHotEqualTo(true).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        example.setOrderByClause("add_time desc");

        PageHelper.startPage(offset, limit);

        return goodsMapper.selectByExampleSelective(example, columns)
                .stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallGoodsAggregate> queryByNew(int offset, int limit) {

        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andIsNewEqualTo(true).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        example.setOrderByClause("add_time desc");
        PageHelper.startPage(offset, limit);

        return goodsMapper.selectByExampleSelective(example, columns)
                .stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallGoodsAggregate> queryByCategory(List<LitemallCategoryId> catList, int offset, int limit) {
        return List.of();
    }

    @Override
    public List<LitemallGoodsAggregate> queryByCategory(LitemallCategoryId categoryId, int offset, int limit) {

        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andCategoryIdEqualTo(categoryId.getId()).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        example.setOrderByClause("add_time desc");
        PageHelper.startPage(offset, limit);

        return goodsMapper.selectByExampleSelective(example, columns).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<Integer> getCategoryIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        LitemallGoodsExample.Criteria criteria1 = example.or();
        LitemallGoodsExample.Criteria criteria2 = example.or();

        if (!StringUtils.isEmpty(brandId)) {
            criteria1.andBrandIdEqualTo(brandId);
            criteria2.andBrandIdEqualTo(brandId);
        }
        if (!StringUtils.isEmpty(isNew)) {
            criteria1.andIsNewEqualTo(isNew);
            criteria2.andIsNewEqualTo(isNew);
        }
        if (!StringUtils.isEmpty(isHot)) {
            criteria1.andIsHotEqualTo(isHot);
            criteria2.andIsHotEqualTo(isHot);
        }
        if (!StringUtils.isEmpty(keywords)) {
            criteria1.andKeywordsLike("%" + keywords + "%");
            criteria2.andNameLike("%" + keywords + "%");
        }
        criteria1.andIsOnSaleEqualTo(true);
        criteria2.andIsOnSaleEqualTo(true);
        criteria1.andDeletedEqualTo(false);
        criteria2.andDeletedEqualTo(false);

        List<LitemallGoods> goodsList = goodsMapper.selectByExampleSelective(example, LitemallGoods.Column.categoryId);
        List<Integer> cats = new ArrayList<Integer>();
        for (LitemallGoods goods : goodsList) {
            cats.add(goods.getCategoryId());
        }
        return cats;
    }


    @Override
    public boolean checkExistByName(String name) {
        return false;
    }

    @Override
    public List<LitemallGoodsAggregate> querySelective(LitemallCategoryId categoryId, LitemallManufacturerId manufacturerId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        LitemallGoodsExample.Criteria criteria1 = example.or();
        LitemallGoodsExample.Criteria criteria2 = example.or();

        if (!StringUtils.isEmpty(categoryId.getId()) && categoryId.getId() != 0) {
            criteria1.andCategoryIdEqualTo(categoryId.getId());
            criteria2.andCategoryIdEqualTo(categoryId.getId());
        }
        if (!StringUtils.isEmpty(manufacturerId.getId())) {
            criteria1.andBrandIdEqualTo(manufacturerId.getId());
            criteria2.andBrandIdEqualTo(manufacturerId.getId());
        }
        if (!StringUtils.isEmpty(isNew)) {
            criteria1.andIsNewEqualTo(isNew);
            criteria2.andIsNewEqualTo(isNew);
        }
        if (!StringUtils.isEmpty(isHot)) {
            criteria1.andIsHotEqualTo(isHot);
            criteria2.andIsHotEqualTo(isHot);
        }
        if (!StringUtils.isEmpty(keywords)) {
            criteria1.andKeywordsLike("%" + keywords + "%");
            criteria2.andNameLike("%" + keywords + "%");
        }
        criteria1.andIsOnSaleEqualTo(true);
        criteria2.andIsOnSaleEqualTo(true);
        criteria1.andDeletedEqualTo(false);
        criteria2.andDeletedEqualTo(false);

        /*if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }*/

        //PageHelper.startPage(offset, limit);

        return goodsMapper.selectByExampleSelective(example, columns).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<LitemallGoodsAggregate> querySelectiveManufacturer() {
        return List.of();
    }

    @Override
    public List<LitemallGoodsAggregate> queryByManufacturer(LitemallManufacturerId manufacturerId, int offset, int limit) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andBrandIdEqualTo(manufacturerId.getId()).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        example.setOrderByClause("add_time desc");
        PageHelper.startPage(offset, limit);
        return goodsMapper.selectByExampleSelective(example, columns).stream().map(this::convertToDomainModel).toList();
    }

    public List<LitemallGoodsAggregate> queryListByCategoryAndManufacturer(LitemallCategoryId catId, LitemallManufacturerId manufacturerId, String keywords, Boolean isHot, Boolean isNew, Integer offset, Integer limit, String sort, String order) {

        LitemallGoodsExample example = new LitemallGoodsExample();
        LitemallGoodsExample.Criteria criteria1 = example.or();
        LitemallGoodsExample.Criteria criteria2 = example.or();

        if (!StringUtils.isEmpty(catId.getId()) && catId.getId() != 0) {
            criteria1.andCategoryIdEqualTo(catId.getId());
            criteria2.andCategoryIdEqualTo(catId.getId());
        }
        if (!StringUtils.isEmpty(manufacturerId.getId())) {
            criteria1.andBrandIdEqualTo(manufacturerId.getId());
            criteria2.andBrandIdEqualTo(manufacturerId.getId());
        }
        if (!StringUtils.isEmpty(isNew)) {
            criteria1.andIsNewEqualTo(isNew);
            criteria2.andIsNewEqualTo(isNew);
        }
        if (!StringUtils.isEmpty(isHot)) {
            criteria1.andIsHotEqualTo(isHot);
            criteria2.andIsHotEqualTo(isHot);
        }
        if (!StringUtils.isEmpty(keywords)) {
            criteria1.andKeywordsLike("%" + keywords + "%");
            criteria2.andNameLike("%" + keywords + "%");
        }
        criteria2.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        criteria1.andIsOnSaleEqualTo(true);
        criteria2.andIsOnSaleEqualTo(true);
        criteria1.andDeletedEqualTo(false);
        criteria2.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(offset, limit);


        return goodsMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }





    /**
     *
     *    --------- Block of utility methods -----------------
     *
     */

    public LitemallGoods convertToDataModel(LitemallGoodsAggregate goodsAggregate) {
        LitemallGoods dataModel = new LitemallGoods();

        if(goodsAggregate.getGoodsId() != null){
            dataModel.setId(goodsAggregate.getGoodsId().getId());
        }
        dataModel.setCategoryId(goodsAggregate.getCategoryId().getId());
        dataModel.setBrandId(goodsAggregate.getManufacturerId().getId());

        dataModel.setGoodsSn(goodsAggregate.getGoodsSn());
        dataModel.setName(goodsAggregate.getGoodsName());
        dataModel.setGallery(goodsAggregate.getGallery());
        dataModel.setKeywords(goodsAggregate.getKeyword());
        dataModel.setBrief(goodsAggregate.getBrief());
        dataModel.setDetail(goodsAggregate.getDetail());

        dataModel.setIsOnSale(goodsAggregate.isOnSale());
        dataModel.setSortOrder(goodsAggregate.getSortOrder());
        dataModel.setPicUrl(goodsAggregate.getPicUrl());
        dataModel.setShareUrl(goodsAggregate.getShareUrl());
        dataModel.setIsHot(goodsAggregate.isHot());
        dataModel.setIsNew(goodsAggregate.isNew());

        dataModel.setCounterPrice(goodsAggregate.getCounterPrice().getAmount());
        dataModel.setRetailPrice(goodsAggregate.getRetailPrice().getAmount());

        dataModel.setAddTime(goodsAggregate.getAddTime());
        dataModel.setUpdateTime(goodsAggregate.getUpdateTime());
        dataModel.setDeleted(goodsAggregate.isDeleted());

        return dataModel;
    }


    public LitemallGoodsAggregate convertToDomainModel(LitemallGoods record) {

        LitemallGoodsAggregate domainModel = new LitemallGoodsAggregate();

        if(record == null){
            return null;
        }

        LitemallCategoryId categoryId = null;
        LitemallManufacturerId manufacturerId = null;

        if (record.getCategoryId() != null && record.getCategoryId() > 0) {
            categoryId = new LitemallCategoryId(record.getCategoryId());
        }

        if(record.getBrandId() != null && record.getBrandId() > 0){
            manufacturerId = new LitemallManufacturerId(record.getBrandId());
        }

        // Handle null 'deleted' field (default to false if null)
        boolean isDeleted = record.getDeleted() != null && record.getDeleted().booleanValue();

        // Skip if marked as deleted (if needed)
        if (isDeleted) {
            return null;
        }



        // Relationship mappings
        domainModel.setGoodsId(new LitemallGoodsId(record.getId()));
        domainModel.setCategoryId(categoryId);
        domainModel.setManufacturerId(manufacturerId);

        domainModel.setGoodsSn(record.getGoodsSn());
        domainModel.setGoodsName(record.getName());

        domainModel.setGallery(record.getGallery());
        domainModel.setKeyword(record.getBrief());
        domainModel.setBrief(record.getBrief());
        domainModel.setDetail(record.getDetail());

        domainModel.setHot(record.getIsHot());
        domainModel.setNew(record.getIsNew());
        domainModel.setUnit(record.getUnit());

        domainModel.setCounterPrice(new LitemallMoney(record.getCounterPrice()));
        domainModel.setRetailPrice(new LitemallMoney(record.getRetailPrice()));

        // Orther fields
        domainModel.setAddTime(record.getAddTime());
        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setDeleted(isDeleted);

        return  domainModel;
    }
}
