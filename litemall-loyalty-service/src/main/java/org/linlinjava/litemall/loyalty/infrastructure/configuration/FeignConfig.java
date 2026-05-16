package org.linlinjava.litemall.loyalty.infrastructure.configuration;

import org.linlinjava.litemall.loyalty.infrastructure.services.feignclients.exception.FeignErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients
public class FeignConfig {

    @Value("${wallet.service.url:http://localhost:8088}")
    private String walletServiceUrl;

    @Bean
    public FeignErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }
}
