package org.linlinjava.litemall.promotion.infrastructure.services.feignclients;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "goods-service", url = "${goods.service.url}")
public interface GoodsServiceFeignClient {

    @GetMapping("/goods/{goodsId}")
    ApiResponse<?> getGoods(@PathVariable Integer goodsId);
}
