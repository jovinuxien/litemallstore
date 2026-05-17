package org.linlinjava.litemall.loyalty.infrastructure.services.feignclients;

import org.linlinjava.litemall.loyalty.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.loyalty.interfaces.dtos.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service", url = "${wallet.service.url}", configuration = FeignConfig.class)
public interface WalletServiceFeignClient {

    @PostMapping("/srv/wallet/{userId}/credit")
    ApiResponse<?> creditWallet(@PathVariable Integer userId, @RequestBody Object command);
}
