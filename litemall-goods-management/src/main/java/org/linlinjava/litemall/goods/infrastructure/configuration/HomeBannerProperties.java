package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Homepage banner derivation (Wave 11): the {@code banner} key of {@code GET /srv/goods/index} is
 * built from the live CJ catalog — top-N L1 category roots by on-sale goods count, hero image from
 * that subtree's newest on-sale goods — instead of the seed {@code litemall_ad} rows whose
 * plain-HTTP yanxuan URLs are mixed-content-broken on the HTTPS storefront.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.home-banner")
public class HomeBannerProperties {
    /** Master switch for GENERATED banners; manual admin rows are served (filtered) either way. */
    private boolean enabled = true;
    /** How many category banners to derive. */
    private int topN = 5;
    /** Roots with fewer on-sale goods than this never become banners (keeps every link non-empty). */
    private long minOnSaleGoods = 20;
    /** Generated-list snapshot TTL. */
    private long cacheTtlMs = 600_000;
    /**
     * Hero images must sit on one of these hosts — exactly the set the edge {@code /_cdn} rewrite
     * (gateway-api CjImageUrlRewriteFilter + Caddy) covers. Anything else (aliyuncs, yanxuan) would
     * bypass the CDN proxy, so such goods are skipped.
     */
    private List<String> allowedImageHosts = List.of("cf.cjdropshipping.com", "oss-cf.cjdropshipping.com");
    /** How many of a root's newest on-sale goods to scan for an allowed-host hero image. */
    private int goodsScanLimit = 20;
}
