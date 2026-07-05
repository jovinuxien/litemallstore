package org.linlinjava.litemall.goods.infrastructure.services.impl;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallCatalogRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LitemallCatalogServiceImpl implements LitemallCatalogService {

    @Autowired
    private LitemallCatalogRepository categoryRepository;
    @Autowired
    private LitemallGoodsRepository goodsRepository;


    @Override
    public LitemallCategoryAggregate getCategoryById(LitemallCategoryId categoryId) {
        return categoryRepository.findById(categoryId);
    }

    @Override
    public List<LitemallCategoryAggregate> getFirstLevelCategories() {
        // 100 = "all of them": the taxonomy holds ~24 L1 roots (native + mirrored CJ trees); the
        // old page size of 10 silently cut off every CJ L1, hiding them from /srv/catalog/all.
        return categoryRepository.queryL1(0, 100);
    }



    @Override
    public List<LitemallCategoryAggregate> getSecondLevelCategories(List<Integer> parentId) {
        return categoryRepository.queryL2ByIds(parentId);
    }

    @Override
    public List<LitemallCategoryAggregate> queryByPid(Integer pid) {
        return categoryRepository.queryByPid(pid);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId) {
        LitemallCategoryAggregate currentCategory = categoryRepository.findById(categoryId);
        if (currentCategory!= null) {
            return goodsRepository.queryByCategory(categoryId, 0, 100);
        }
        return List.of();
    }
}
