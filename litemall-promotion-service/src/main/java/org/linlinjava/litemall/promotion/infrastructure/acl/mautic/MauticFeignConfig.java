package org.linlinjava.litemall.promotion.infrastructure.acl.mautic;

import feign.auth.BasicAuthRequestInterceptor;
import org.linlinjava.litemall.promotion.infrastructure.configuration.MauticProperties;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration adding HTTP Basic auth to every Mautic call,
 * from {@link MauticProperties} (credentials env-sourced, never hardcoded). Not a
 * {@code @Configuration} — referenced only via the {@code configuration} attribute
 * of {@link MauticClient}, so it does not leak into the global Feign context.
 */
public class MauticFeignConfig {

    @Bean
    public BasicAuthRequestInterceptor mauticBasicAuthInterceptor(MauticProperties properties) {
        String username = properties.getUsername() != null ? properties.getUsername() : "";
        String password = properties.getPassword() != null ? properties.getPassword() : "";
        return new BasicAuthRequestInterceptor(username, password);
    }
}
