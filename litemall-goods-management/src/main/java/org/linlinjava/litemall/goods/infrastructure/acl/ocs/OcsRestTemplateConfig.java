package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Dedicated {@link RestTemplate} for the OCS ACL clients so that any future
 * interceptor (timeouts, retries, correlation-id propagation) lives in one
 * place and doesn't affect other RestTemplate users in the module.
 */
@Configuration
public class OcsRestTemplateConfig {

    @Bean
    public RestTemplate ocsRestTemplate() {
        return new RestTemplate();
    }
}
