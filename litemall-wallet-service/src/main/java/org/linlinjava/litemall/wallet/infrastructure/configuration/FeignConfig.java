package org.linlinjava.litemall.wallet.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients
public class FeignConfig {

    @Value("${user.service.url:http://localhost:8081}")
    private String userServiceUrl;
}
