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
     * Attach a service-to-service machine token to outbound Feign calls aimed at our
     * INTERNAL svcsecurity resource-servers (e.g. goods-management), so they accept the
     * call instead of returning 401. Token is fetched/cached by {@link GoodsMachineTokenProvider}.
     *
     * <p>It MUST NOT be sent to external third parties: the CJ Dropshipping clients
     * ({@code cj-*}) authenticate with their own {@code CJ-Access-Token} header, and
     * relaying our internal Bearer there both leaks the machine token and trips CJ's
     * gateway (surfacing as "transport/auth failure"). So skip CJ targets by client name.
     */
    @Bean
    public RequestInterceptor goodsMachineTokenInterceptor(GoodsMachineTokenProvider tokenProvider) {
        return template -> {
            feign.Target<?> target = template.feignTarget();
            String clientName = target != null ? target.name() : "";
            if (clientName != null && clientName.startsWith("cj-")) {
                return; // external CJ client — never relay the internal machine token
            }
            template.header("Authorization", "Bearer " + tokenProvider.getToken());
        };
    }
}
