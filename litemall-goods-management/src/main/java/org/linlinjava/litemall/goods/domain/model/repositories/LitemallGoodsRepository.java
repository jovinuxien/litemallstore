package org.linlinjava.litemall.goods.domain.model.repositories;


import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;

import java.util.List;

public interface LitemallGoodsRepository {


    public void addGoods(LitemallGoodsAggregate goodsAggregate);
    public int count();
    public int updateById(LitemallGoodsAggregate goodsAggregate);
    public void deleteById(LitemallGoodsId goodsId);

    public LitemallGoodsAggregate findById(LitemallGoodsId goodsId);

    public int queryOnSale();

    public List<LitemallGoodsAggregate> queryByIds(List<LitemallGoodsId> ids);

    public List<LitemallGoodsAggregate> queryByHot(int offset, int limit);

    public List<LitemallGoodsAggregate> queryByNew(int offset, int limit);

    public List<LitemallGoodsAggregate> queryByCategory(List<LitemallCategoryId> catList, int offset, int limit);

    public List<LitemallGoodsAggregate> queryByCategory(LitemallCategoryId categoryId, int offset, int limit);

    public List<Integer> getCategoryIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew);

    public boolean checkExistByName(String name);

    public List<LitemallGoodsAggregate> querySelectiveManufacturer();

    public List<LitemallGoodsAggregate> queryListByCategoryAndManufacturer(LitemallCategoryId catId, LitemallManufacturerId manufacturerId, String keywords, Boolean isHot, Boolean isNew, Integer offset, Integer limit, String sort, String order);

    public List<LitemallGoodsAggregate> queryByManufacturer(LitemallManufacturerId manufacturerId, int offset, int limit);
}
