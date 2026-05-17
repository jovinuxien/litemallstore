package org.linlinjava.litemall.goods.infrastructure.services.api;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;

import java.util.List;

public interface LitemallCatalogService {

    /**
     * Api methods for Category Management
     */
    LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId);
    List<LitemallCategoryAggregate> getFirstLevelCategories();
    List<LitemallCategoryAggregate> getSecondLevelCategories(List<Integer> catIds);
    List<LitemallCategoryAggregate> queryByPid(Integer pid);
    List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId);

}
