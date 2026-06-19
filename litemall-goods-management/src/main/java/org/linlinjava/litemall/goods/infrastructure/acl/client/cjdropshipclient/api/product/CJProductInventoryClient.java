package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * CJ Dropshipping inventory/stock client — the one product capability the ACL was missing.
 *
 * <p>Wraps CJ's {@code GET /api2.0/v1/product/stock/queryByVid?vid=<vid>} ("Query Inventory By
 * Variant"): the response carries one {@link org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryData}
 * per warehouse area for that variant, each with a {@code storageNum}. CJ inventory is keyed by
 * VARIANT id ({@code vid}), so real per-SKU stock costs one call per variant — pacing/caching is the
 * caller's responsibility ({@code CJProductService#getInventory}), and the enrichment job batches +
 * paces these to stay within the CJ daily quota. Mirrors {@link CJProductClient} (token-auth via
 * {@code CJ-Access-Token}, config-driven URL, Resilience4j limiter; no hardcoded host).
 */
@Component
public class CJProductInventoryClient extends CJRequestUtils {

    private static final Logger logger = LoggerFactory.getLogger(CJProductInventoryClient.class);

    private final CJDropshippingConfig config;
    private final CJTokenService cjTokenService;

    // RateLimiter: 1 request per 300 seconds (matches the other CJ clients' conservative default).
    private final RateLimiter rateLimiter;

    public CJProductInventoryClient(CJDropshippingConfig conf, RestTemplate restTemp, ObjectMapper mapp,
                                    CJTokenService cjTokenService) {
        super(restTemp, mapp);
        this.config = conf;
        this.cjTokenService = cjTokenService;

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(300))
                .limitForPeriod(1)
                .timeoutDuration(Duration.ofSeconds(0))
                .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        this.rateLimiter = rateLimiterRegistry.rateLimiter("cjInventoryRateLimiter");
    }

    /**
     * Fetch a single CJ variant's warehouse inventory by its {@code vid} (UUID String). Returns the
     * raw CJ response (a per-area list of {@code storageNum}); the caller normalizes it to a single
     * stock figure. Pacing/caching is the caller's responsibility (see {@code CJProductService}).
     */
    public CJInventoryDataResponse queryByVid(String vid) {
        try {
            String accessToken = cjTokenService.getValidToken();
            String stockUrl = config.getStockQueryUrl();

            if (stockUrl == null || !stockUrl.matches("^https?://.*")) {
                throw new IllegalArgumentException("Stock query URL must be absolute (include http:// or https://)");
            }
            String url = UriComponentsBuilder.fromUriString(stockUrl)
                    .queryParam("vid", vid)
                    .build().toUriString();
            return makeGetRequest(url, CJInventoryDataResponse.class, accessToken, "Failed to fetch inventory");
        } catch (Exception e) {
            throw new RuntimeException("Inventory fetch failed: " + e.getMessage(), e);
        }
    }
}
