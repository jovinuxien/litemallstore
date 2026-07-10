package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo.CJProductVideoRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo.CJProductVideoResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CjTimedRestTemplates;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Component;

/**
 * CJ "Product Video" client — per-product videos by UUID {@code pid} (POST, JSON body).
 * Hits {@code spring.cjdropship.api.product.product-videos-url}. Pacing/caching is the caller's
 * responsibility (see {@code CJProductService#getProductVideos}). Uses a timeout-configured
 * RestTemplate ({@link CjTimedRestTemplates}).
 */
@Component
public class CJProductVideoClient extends CJRequestUtils {

    private final CJDropshippingConfig config;
    private final CJTokenService cjTokenService;

    public CJProductVideoClient(CJDropshippingConfig conf, ObjectMapper mapp, CJTokenService cjTokenService) {
        super(CjTimedRestTemplates.withDefaultTimeouts(), mapp);
        this.config = conf;
        this.cjTokenService = cjTokenService;
    }

    public CJProductVideoResponse queryVideosByProductId(String pid) {
        try {
            String accessToken = cjTokenService.getValidToken();
            String videosUrl = config.getProductVideosUrl();
            if (videosUrl == null || !videosUrl.matches("^https?://.*")) {
                throw new IllegalArgumentException("Product videos URL must be absolute (include http:// or https://)");
            }
            CJProductVideoRequest request = new CJProductVideoRequest();
            request.setProductId(pid);
            return makePostRequest(videosUrl, request, CJProductVideoResponse.class, accessToken,
                    "Failed to fetch product videos");
        } catch (Exception e) {
            throw new RuntimeException("Product videos fetch failed: " + e.getMessage(), e);
        }
    }
}
