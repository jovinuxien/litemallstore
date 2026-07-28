package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Public-facing site identity (Wave 13). {@code litemall.public-base-url} is the absolute
 * origin minted into sitemap {@code <loc>} URLs; overridable via env
 * {@code LITEMALL_PUBLIC_BASE_URL} (relaxed binding). The default is the live storefront,
 * so prod needs no extra env.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall")
public class PublicSiteProperties {
    private String publicBaseUrl = "https://trovemo.com";
}
