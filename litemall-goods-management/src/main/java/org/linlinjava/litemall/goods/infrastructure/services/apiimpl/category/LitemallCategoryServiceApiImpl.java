package org.linlinjava.litemall.goods.infrastructure.services.apiimpl.category;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallCategoryRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.interfaces.api.category.LitemallCategoryServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LitemallCategoryServiceApiImpl implements LitemallCategoryServiceApi {

    @Autowired
    private LitemallCategoryRepository categoryRepository;

    @Override
    public LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId) {
        return categoryRepository.findById(categoryId);
    }

    @Override
    public List<LitemallCategoryAggregate> getFirstLevelCategories() {
        return categoryRepository.queryL1(0, 10) ;
    }

    @Override
    public List<LitemallCategoryAggregate> getSecondLevelCategories() {
        return List.of();
    }

    @Override
    public List<LitemallCategoryAggregate> queryByPid(LitemallCategoryId pid) {
        return List.of();
    }
}
