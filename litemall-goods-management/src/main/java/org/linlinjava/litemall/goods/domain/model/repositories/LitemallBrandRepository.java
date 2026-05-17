package org.linlinjava.litemall.goods.domain.model.repositories;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallBrandAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;

import java.util.List;

public interface LitemallBrandRepository {


    void add (LitemallBrandAggregate brandAggregate);
    int update(LitemallBrandAggregate brandAggregate);
    void removeById(LitemallBrandAggregate brandAggregate);

    LitemallBrandAggregate findById(LitemallManufacturerId id);

    List<LitemallBrandAggregate> findAll();
    List<LitemallBrandAggregate> querySelective(String id, String name, Integer page, Integer size, String sort, String order);

}
