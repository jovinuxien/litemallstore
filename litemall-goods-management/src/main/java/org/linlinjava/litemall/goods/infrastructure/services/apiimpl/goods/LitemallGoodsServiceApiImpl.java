package org.linlinjava.litemall.goods.infrastructure.services.apiimpl.goods;

import org.checkerframework.checker.units.qual.A;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.interfaces.api.goods.LitemallGoodsServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LitemallGoodsServiceApiImpl implements LitemallGoodsServiceApi {

    @Autowired
    private LitemallGoodsRepository goodsRepository;

    @Override
    public List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId) {
        return goodsRepository.queryByCategory(categoryId, 0, 100);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId, Integer offset, Integer limit) {
        return goodsRepository.queryByCategory(categoryId, offset, limit);
    }

    @Override
    public int getGoodsOnSale() {
        return goodsRepository.queryOnSale();
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByBrand(LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort, String order) {
        return goodsRepository.queryByManufacturer(brandId, page * size, size);
    }

    @Override
    public LitemallGoodsAggregate getGoodsById(LitemallGoodsId goodsId) {
        return goodsRepository.findById(goodsId);
    }

    @Override
    public List<LitemallGoodsAggregate> getAllGoodByIds(List<LitemallGoodsId> ids) {
        return goodsRepository.queryByIds(ids);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByHot() {
        return goodsRepository.queryByHot(0, 10);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByNew() {
        return goodsRepository.queryByNew(0, 10);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsBySelective(LitemallCategoryId catId, LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort) {
        return goodsRepository.querySelective(catId, brandId, keywords, isHot, isNew, page, size, sort);
    }

    @Override
    public List<LitemallCategoryAggregate> getCatIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew) {
        return List.of();
    }
}
