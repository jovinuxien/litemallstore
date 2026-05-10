package org.linlinjava.litemall.goods.infrastructure.services.api.brand;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallBrandAggregate;

import java.util.List;

public interface LitemallBrandServiceApi {

    /**
     * Api methods for Brand Management
     */
    List<LitemallBrandAggregate> getListBrands();
    LitemallBrandAggregate getBrandById(Long id);
}
