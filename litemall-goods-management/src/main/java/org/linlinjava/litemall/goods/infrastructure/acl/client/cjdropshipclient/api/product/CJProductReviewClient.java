package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * CJ "Product Comments" client — per-product customer reviews by UUID {@code pid} (paged).
 * Hits {@code spring.cjdropship.api.product.comments-url}. Pacing/caching is the caller's
 * responsibility (see {@code CJProductService#getProductComments}).
 */
@Component
public class CJProductReviewClient extends CJRequestUtils {

    private final CJDropshippingConfig config;
    private final CJTokenService cjTokenService;

    public CJProductReviewClient(CJDropshippingConfig conf, RestTemplate restTemp, ObjectMapper mapp,
                                 CJTokenService cjTokenService) {
        super(restTemp, mapp);
        this.config = conf;
        this.cjTokenService = cjTokenService;
    }

    public CJProductReviewDataResponse getProductComments(String pid, int pageNum, int pageSize) {
        try {
            String accessToken = cjTokenService.getValidToken();
            String commentsUrl = config.getProductCommentsUrl();

            if (commentsUrl == null || !commentsUrl.matches("^https?://.*")) {
                throw new IllegalArgumentException("Product comments URL must be absolute (include http:// or https://)");
            }
            String url = UriComponentsBuilder.fromUriString(commentsUrl)
                    .queryParam("pid", pid)
                    .queryParam("pageNum", pageNum)
                    .queryParam("pageSize", pageSize)
                    .build().toUriString();
            return makeGetRequest(url, CJProductReviewDataResponse.class, accessToken, "Failed to fetch product comments");
        } catch (Exception e) {
            throw new RuntimeException("Product comments fetch failed: " + e.getMessage(), e);
        }
    }
}
