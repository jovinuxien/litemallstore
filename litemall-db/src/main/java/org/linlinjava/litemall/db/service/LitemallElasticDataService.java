package org.linlinjava.litemall.db.service;


import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsExample;
import org.linlinjava.litemall.db.dto.ElasticDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class LitemallElasticDataService {
    @Resource
    private LitemallGoodsMapper goodsMapper;
    @Resource
    private LitemallCategoryMapper categoryMapper;
    @Resource
    private LitemallElasticMapper elasticDataMapper;
    @Resource
    private LitemallBrandMapper brandMapper;
    @Resource
    private LitemallGoodsAttributeMapper goodsAttributeMapper;



    public List<LitemallGoods> getGoodsByCategory(Integer catId) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andCategoryIdEqualTo(catId);
        return  goodsMapper.selectByExample(example);
    }

    public List<LitemallGoods> getGoodsByBrand(Integer brandId) {
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andBrandIdEqualTo(brandId);
        return  goodsMapper.selectByExample(example);
    }



    public List<LitemallCategory> getCategoriesForGoods(Integer goodsId) {
        LitemallGoods goods = goodsMapper.selectByPrimaryKey(goodsId);
        List<LitemallCategory> categoryList = new ArrayList<>();
        if (goods != null) {
            LitemallCategory category = categoryMapper.selectByPrimaryKey(goods.getCategoryId());
            // We might to return a list of parent categories as well
            // This would require traversing up the category tree
            categoryList.add(category);
            return categoryList;
        }
        return categoryList;
    }

    public List<ElasticDto> getGoods(String manufacturerName, String attribute) {
        return elasticDataMapper.selectGoodsWithJoin(manufacturerName, attribute);
    }



}
