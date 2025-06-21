package org.linlinjava.litemall.order.infrastructure.services.feignclients;


import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.constraints.NotNull;

@FeignClient("litemall-goods-management")
public interface GoodsServiceFeignClient {

    @RequestMapping(method = RequestMethod.GET,
            value = "/goods/goodsdetail",
            consumes = "application/json")
    Object getGoodsAggregate(@NotNull Integer goodsId);

    @RequestMapping(method = RequestMethod.GET,
            value = "/goods/attribute",
            consumes = "application/json")
    Object getGoodsAttributeAggregate(@NotNull Integer goodsId);

    @RequestMapping(method = RequestMethod.GET,
            value = "/goods/product",
            consumes = "application/json")
    Object getGoodsProductAggregate(@NotNull Integer goodsId);
}
