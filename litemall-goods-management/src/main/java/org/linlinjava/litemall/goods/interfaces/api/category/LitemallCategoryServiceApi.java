package org.linlinjava.litemall.goods.interfaces.api.category;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;

import java.util.List;

public interface LitemallCategoryServiceApi {

    /**
     * Api methods for Category Management
     */
    LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId);
    List<LitemallCategoryAggregate> getFirstLevelCategories();
    List<LitemallCategoryAggregate> getSecondLevelCategories();
    List<LitemallCategoryAggregate> queryByPid(LitemallCategoryId pid);
}
