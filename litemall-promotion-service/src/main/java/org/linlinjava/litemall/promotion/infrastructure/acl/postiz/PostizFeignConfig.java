package org.linlinjava.litemall.promotion.infrastructure.acl.postiz;

import feign.RequestInterceptor;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PostizProperties;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration adding the Postiz public-API key to every
 * call, from {@link PostizProperties} (env-sourced, never hardcoded). The REST
 * API takes the key as a BARE {@code Authorization} value — adding a
 * {@code Bearer} prefix breaks auth (only Postiz's MCP endpoint uses Bearer).
 * Not a {@code @Configuration} — referenced only via the {@code configuration}
 * attribute of {@link PostizClient}, so it does not leak into the global Feign
 * context.
 */
public class PostizFeignConfig {

    @Bean
    public RequestInterceptor postizAuthInterceptor(PostizProperties properties) {
        return template -> {
            String apiKey = properties.getApiKey();
            template.header("Authorization", apiKey != null ? apiKey : "");
        };
    }
}
