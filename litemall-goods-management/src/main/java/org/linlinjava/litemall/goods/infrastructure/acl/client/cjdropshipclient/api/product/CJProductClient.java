package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
@Getter
@Setter
public class CJProductClient extends CJRequestUtils {

    private static final Logger logger = LoggerFactory.getLogger(CJProductClient.class);

    private final CJDropshippingConfig config;
    private final CJTokenService cjTokenService;
    private String cachedAccessToken;
    private long tokenExpiryTime;

    // RateLimiter: 1 request per 300 seconds
    private final RateLimiter rateLimiter;


    
    public CJProductClient(CJDropshippingConfig conf, RestTemplate restTemp, ObjectMapper mapp, CJTokenService cjTokenService) {
        super(restTemp, mapp);
        this.config = conf;
        this.cjTokenService = cjTokenService;

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
               .limitRefreshPeriod(Duration.ofSeconds(300))
               .limitForPeriod(1)
               .timeoutDuration(Duration.ofSeconds(0))
               .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        this.rateLimiter = rateLimiterRegistry.rateLimiter("cjDropshippingRateLimiter");
    }


    public CJProductDataResponse getProductList() {
        try {
            String accessToken = cjTokenService.getValidToken(); // This handles all token logic
            String productUrl = config.getProductListUrl();

            logger.debug("the token accessed  is {}: ", accessToken);

            // Validate URL
            if (!productUrl.matches("^https?://.*")) {
                throw new IllegalArgumentException("Product URL must be absolute (include http:// or https://)");
            }
            return makeGetRequest(productUrl, CJProductDataResponse.class, accessToken, "Failed to fetch product list");
        } catch (Exception e) {
            throw new RuntimeException("Product fetch failed: " + e.getMessage(), e);
        }
    }

    public CJCategoryDataResponse getCategoryList() {
        try{
            String accessToken = cjTokenService.getValidToken();
            String categoryUrl = config.getCategoryListUrl();

            // Validate URL
            if (!categoryUrl.matches("^https?://.*")) {
                throw new IllegalArgumentException("Category URL must be absolute (include http:// or https://)");
            }
            return makeGetRequest(categoryUrl, CJCategoryDataResponse.class, accessToken, "Failed to fetch category list");
        } catch (Exception e) {
            throw new RuntimeException("Category fetch failed: " + e.getMessage(), e);
        }
    }


}
