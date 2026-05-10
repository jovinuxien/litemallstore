package org.linlinjava.litemall.goods.infrastructure.services.apiimpl;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallBrandAggregate;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallBrandServiceApi;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class LitemallBrandServiceImpl implements LitemallBrandServiceApi {


    @Override
    public List<LitemallBrandAggregate> getListBrands() {
        return List.of();
    }

    @Override
    public LitemallBrandAggregate getBrandById(Long id) {
        return null;
    }
}
