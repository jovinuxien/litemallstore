package org.linlinjava.litemall.goods.infrastructure.services.apiimpl.goods;

import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsAttributeRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.interfaces.api.goods.LitemallGoodsAttributeServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class LitemallGoodsAttributeServiceApiImpl implements LitemallGoodsAttributeServiceApi {

    @Autowired
    private LitemallGoodsAttributeRepository goodsAttributeRepository;

    @Override
    public List<LitemallGoodsAttributeAggregate> getByGoodsId(LitemallGoodsId goodsId) {
        return goodsAttributeRepository.queryByGoodsId(goodsId);
    }
}
