package org.linlinjava.litemall.goods.infrastructure.services.api;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallBrandAggregate;

import java.util.List;

public interface LitemallBrandServiceApi {

    /**
     * Api methods for Brand Management
     */
    List<LitemallBrandAggregate> getListBrands();
    LitemallBrandAggregate getBrandById(Long id);
}
