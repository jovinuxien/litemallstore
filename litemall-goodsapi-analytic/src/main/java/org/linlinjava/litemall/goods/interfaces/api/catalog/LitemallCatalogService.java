package org.linlinjava.litemall.goods.interfaces.api.catalog;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;

import java.util.List;

public interface LitemallCatalogService {

    /**
     * Api methods for Category Management
     */
    LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId);
    List<LitemallCategoryAggregate> getFirstLevelCategories();
    List<LitemallCategoryAggregate> getSecondLevelCategories(Integer parentId);
    List<LitemallCategoryAggregate> queryByPid(Integer pid);
    List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId);
    
}
