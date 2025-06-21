package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
@Getter
@Setter
public class CJProductClient extends CJRequestUtils {


    private final CJDropshippingConfig config;
    private final CJAuthenticationService authenticationService;
    private String cachedAccessToken;
    private long tokenExpiryTime;

    // RateLimiter: 1 request per 300 seconds
    private final RateLimiter rateLimiter;

    public CJProductClient(CJDropshippingConfig conf, RestTemplate restTemp, ObjectMapper mapp, CJAuthenticationService authService) {
        super(restTemp, mapp);
        this.config = conf;
        this.authenticationService = authService;

        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(300))
                .limitForPeriod(1)
                .timeoutDuration(Duration.ofSeconds(0))
                .build();
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);
        this.rateLimiter = rateLimiterRegistry.rateLimiter("cjDropshippingRateLimiter");
    }

    // Method to get a list of products
    public CJProductDataResponse getProductList() {
        try{
            rateLimiter.acquirePermission(); // Wait until the rate limit allows the request
            String accessToken = getAccessToken();
            String errorMessage = "Failed to get product by ID: ";
            return makeGetRequest(config.getProductListUrl(), CJProductDataResponse.class, accessToken, errorMessage);
        } catch (Exception e){
            // Log the error for debugging
            System.err.println("Error in getProductList: " + e.getMessage());
            throw new RuntimeException("Failed to fetch product list: " + e.getMessage(), e);
        }

    }

    // Method to get a specific product by ID
    public CJProductDetailResponse getProductById(long productId) {

        try {
            rateLimiter.acquirePermission(); // Wait until the rate limit allows the request
            String accessToken = getAccessToken();
            String errorMessage = "Failed to get product by ID: " + productId;
            String queryUrl = config.getProductDetailUrl() + "/query?pid=" + productId;
            return makeGetRequest(queryUrl, CJProductDetailResponse.class, accessToken, errorMessage);
        } catch (Exception e) {
            System.err.println("Error in getProductById: " + e.getMessage());
            throw new RuntimeException("Failed to fetch product by ID: " + e.getMessage(), e);
        }
    }

    public String getAccessToken() {
        if(cachedAccessToken == null || System.currentTimeMillis() > tokenExpiryTime){
           /* try{
                Thread.sleep(300000);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                throw new RuntimeException("Delay interrupted ", e);
            }*/

            authenticationService.accessToken(config.getCjEmail(), config.getCjApiKey());
            cachedAccessToken = authenticationService.getAccessToken(config.getCjEmail());
            tokenExpiryTime = System.currentTimeMillis() + 300000;
        }
        System.out.println("the access from the CJProduct client is " + cachedAccessToken);
        return cachedAccessToken;
    }

}
