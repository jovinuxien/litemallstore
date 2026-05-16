package org.linlinjava.litemall.wallet.infrastructure.services.feignclients;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.wallet.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.wallet.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.wallet.infrastructure.configuration.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${user.service.url:http://localhost:8081}", configuration = FeignConfig.class)
public interface UserServiceFeignClient {

    @GetMapping(value = "/srv/user/{userId}")
    ApiResponse<LitemallUserAggregate> getUserById(@NotNull @PathVariable("userId") Integer userId);

    @GetMapping(value = "/srv/user/by-username/{username}")
    ApiResponse<LitemallUserAggregate> getUserByUsername(@NotNull @PathVariable("username") String username);
}
