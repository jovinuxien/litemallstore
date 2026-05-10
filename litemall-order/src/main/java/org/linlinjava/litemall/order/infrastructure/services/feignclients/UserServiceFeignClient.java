package org.linlinjava.litemall.order.infrastructure.services.feignclients;

import org.linlinjava.litemall.order.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;


@FeignClient(name = "user-service", url = "${goods.service.url}", configuration = FeignConfig.class)
public interface UserServiceFeignClient {

    @GetMapping(value = "/goods/goodsdetail")
    ApiResponse<LitemallUserAggregate> geUserById(@NotNull Integer goodsId);

    @GetMapping(value = "/goods/goodsdetail")
    ApiResponse<LitemallUserAggregate> getUserByUsername(@NotNull String username);

    @PostMapping(value = "/goods/stock/reduce")
    ApiResponse<Void> saveUser(@RequestBody ReduceStockRequest request);
}


