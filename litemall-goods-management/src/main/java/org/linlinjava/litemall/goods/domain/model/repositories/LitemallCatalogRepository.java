package org.linlinjava.litemall.goods.domain.model.repositories;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;

import java.util.List;

public interface LitemallCatalogRepository {

    void save(LitemallCategoryAggregate categoryAggregate);
    int updateById(LitemallCategoryAggregate categoryAggregate);
    void removeById(LitemallCategoryId id);

    List<LitemallCategoryAggregate> queryL1(Integer offset, Integer limit);
    List<LitemallCategoryAggregate> queryByPid(Integer pid);
    List<LitemallCategoryAggregate> queryL2ByIds(List<Integer> ids);
    LitemallCategoryAggregate findById(LitemallCategoryId id);

    List<LitemallCategoryAggregate> querySelective(String id, String name, Integer page, Integer size, String sort, String order);

    List<LitemallCategoryAggregate> queryChannel();
}
