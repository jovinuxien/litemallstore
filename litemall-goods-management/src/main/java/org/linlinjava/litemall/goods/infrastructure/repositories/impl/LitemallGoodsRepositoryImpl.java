package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.*;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
public class LitemallGoodsRepositoryImpl implements LitemallGoodsRepository {

    LitemallGoods.Column[] columns = new LitemallGoods.Column[]{LitemallGoods.Column.id, LitemallGoods.Column.goodsSn,  LitemallGoods.Column.name, LitemallGoods.Column.categoryId, LitemallGoods.Column.brandId, LitemallGoods.Column.gallery, LitemallGoods.Column.keywords, LitemallGoods.Column.brief, LitemallGoods.Column.isOnSale, LitemallGoods.Column.sortOrder, LitemallGoods.Column.picUrl, LitemallGoods.Column.shareUrl, LitemallGoods.Column.isHot, LitemallGoods.Column.isNew, LitemallGoods.Column.counterPrice, LitemallGoods.Column.retailPrice, LitemallGoods.Column.addTime, LitemallGoods.Column.updateTime, LitemallGoods.Column.deleted, LitemallGoods.Column.detail};
    //LitemallGoods.Column[] columns = new LitemallGoods.Column[]{LitemallGoods.Column.id, LitemallGoods.Column.name, LitemallGoods.Column.brief, LitemallGoods.Column.picUrl, LitemallGoods.Column.isHot, LitemallGoods.Column.isNew, LitemallGoods.Column.counterPrice, LitemallGoods.Column.retailPrice};
    @Resource
    private final LitemallGoodsMapper goodsMapper;


    public LitemallGoodsRepositoryImpl(LitemallGoodsMapper goodsMapper){
        this.goodsMapper = goodsMapper;
    }


    @Override
    public void insertGoods(LitemallGoodsAggregate goodsAggregate) {
        goodsMapper.insertSelective(convertToDataModel(goodsAggregate));
    }

    @Override
    public int count() {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andDeletedEqualTo(false);
        return (int) goodsMapper.countByExample(example);
    }

    @Override
    public int updateById(LitemallGoodsAggregate goodsAggregate) {
        return goodsMapper.updateByPrimaryKeySelective(convertToDataModel(goodsAggregate));
    }

    @Override
    public void deleteById(LitemallGoodsId goodsId) {
        goodsMapper.logicalDeleteByPrimaryKey(goodsId.getId());
    }

    @Override
    public LitemallGoodsAggregate findById(LitemallGoodsId goodsId) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andIdEqualTo(goodsId.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(goodsMapper.selectOneByExampleWithBLOBs(example));
    }

    @Override
    public int queryOnSale() {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        return (int) goodsMapper.countByExample(example);
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
        example.or().andCategoryIdEqualTo(Integer.valueOf(categoryId.getId())).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        example.setOrderByClause("add_time desc");
        PageHelper.startPage(offset, limit);

        return goodsMapper.selectByExampleSelective(example, columns).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public List<Integer> getCatsId(Integer brandId, String keywords, Boolean isHot, Boolean isNew) {
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

    /**
     * SQL like version
     * SELECT COUNT(*) FROM litemall_goods WHERE name = ? AND is_on_sale = true AND deleted = false and returning an integer representing the number of rows found in the database
     * the !=0 is the boolean comparison that checks if the count returned is not zero
     * The entire expression returns a boolean value (true or false)
     *
     * @param name
     * @return
     */
    private boolean checkExistByGoodsName(String name) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andNameEqualTo(name).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        return goodsMapper.countByExample(example) !=0;
    }


    @Override
    public boolean checkExistByName(String name) {
        return checkExistByGoodsName(name);
    }

    @Override
    public List<LitemallGoodsAggregate> querySelective(LitemallCategoryId categoryId, LitemallManufacturerId manufacturerId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        LitemallGoodsExample.Criteria criteria1 = example.or();
        LitemallGoodsExample.Criteria criteria2 = example.or();

        // LitemallGoodsController#listGoods passes manufacturerId=null when the
        // ?brandId= query param is absent — "no brand filter". Guard the deref
        // so the optional filter doesn't NPE on the unfiltered code path.
        if (manufacturerId != null && manufacturerId.getId() != null) {
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

  /*  @Override
    public List<LitemallGoodsAggregate> querySelectiveManufacturer(Integer manufacturerId, int offset, int limit) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andBrandIdEqualTo(manufacturerId).andIsOnSaleEqualTo(true).andDeletedEqualTo(false);
        example.setOrderByClause("add_time desc");
        PageHelper.startPage(offset, limit);
        return goodsMapper.selectByExampleSelective(example, columns).stream().map(this::convertToDomainModel).toList();
        //return List.of();
    }*/

    @Override
    public List<LitemallGoodsAggregate> queryByManufacturer(LitemallManufacturerId manufacturerId, int offset, int limit) {
        // Method contract requires a manufacturer filter (vs querySelective for
        // optional filters). Fail fast instead of silently returning every good.
        Objects.requireNonNull(manufacturerId, "manufacturerId");
        Objects.requireNonNull(manufacturerId.getId(), "manufacturerId.id");
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

        // Same optional-filter guard as querySelective: callers may pass null
        // when the corresponding query param is absent.
        if (manufacturerId != null && manufacturerId.getId() != null) {
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
        dataModel.setCategoryId(Integer.valueOf(goodsAggregate.getCategoryId().getId()));
        dataModel.setBrandId(goodsAggregate.getManufacturerId().getId());

        dataModel.setGoodsSn(goodsAggregate.getGoodsSn());
        dataModel.setName(goodsAggregate.getGoodsName());
        dataModel.setGallery(goodsAggregate.getGallery());
        dataModel.setKeywords(goodsAggregate.getKeyword());
        dataModel.setBrief(goodsAggregate.getBrief());

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
        dataModel.setDetail(goodsAggregate.getDetail());


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
        manufacturerId = new LitemallManufacturerId(record.getBrandId());

      /*  if(record.getBrandId() != null && record.getBrandId() > 0){
            manufacturerId = new LitemallManufacturerId(record.getBrandId());
        }*/



        // Handle null 'deleted' field (default to false if null)
        boolean isDeleted = record.getDeleted() != null && record.getDeleted().booleanValue();

        // Skip if marked as deleted (if needed)
        if (isDeleted) {
            return null;
        }



        // Relationship mappings
        domainModel.setGoodsId(new LitemallGoodsId(record.getId()));
        domainModel.setGoodsSn(record.getGoodsSn());
        domainModel.setGoodsName(record.getName());
        domainModel.setCategoryId(categoryId);
        domainModel.setManufacturerId(manufacturerId);
        //domainModel.setManufacturerId(record.getBrandId());

        domainModel.setGallery(record.getGallery());
        domainModel.setKeyword(record.getKeywords());
        domainModel.setBrief(record.getBrief());
        domainModel.setOnSale(record.getIsOnSale());
        domainModel.setSortOrder(record.getSortOrder());

        domainModel.setPicUrl(record.getPicUrl());
        domainModel.setShareUrl(record.getShareUrl());

        domainModel.setHot(record.getIsHot());
        domainModel.setNew(record.getIsNew());
        domainModel.setUnit(record.getUnit());

        domainModel.setCounterPrice(new LitemallMoney(record.getCounterPrice()));
        domainModel.setRetailPrice(new LitemallMoney(record.getRetailPrice()));

        // Orther fields
        domainModel.setAddTime(record.getAddTime());
        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setDeleted(isDeleted);
        domainModel.setDetail(record.getDetail());


        return  domainModel;
    }
}
