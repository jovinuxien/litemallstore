package org.linlinjava.litemall.goods.infrastructure.services.apiimpl.goods;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsProductRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.interfaces.api.goods.LitemallGoodsProductServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class LitemallGoodsProductServiceApiImpl implements LitemallGoodsProductServiceApi {

    @Autowired
    private LitemallGoodsProductRepository goodsProductRepository;


    @Override
    public List<LitemallGoodsProductAggregate> getByGoodsId(LitemallGoodsId goodsId) {
        return goodsProductRepository.findByGoodsId(goodsId);
    }
}
