package org.linlinjava.litemall.order.infrastructure.configuration;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import feign.RequestInterceptor;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.exception.FeignErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients
public class FeignConfig {


    @Value("${goods.service.url:http://localhost:8082}")
    private String goodsServiceUrl;

    @Bean
    public FeignErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Attach a service-to-service machine token to every outbound Feign call so
     * goods-management (a svcsecurity resource-server) accepts them instead of
     * returning 401. Token is fetched/cached by {@link GoodsMachineTokenProvider}.
     */
    @Bean
    public RequestInterceptor goodsMachineTokenInterceptor(GoodsMachineTokenProvider tokenProvider) {
        return template -> template.header("Authorization", "Bearer " + tokenProvider.getToken());
    }
}
