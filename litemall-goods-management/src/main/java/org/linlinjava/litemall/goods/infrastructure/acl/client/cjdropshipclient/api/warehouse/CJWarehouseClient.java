package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.warehouse.CJWarehouseDetailResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CjTimedRestTemplates;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * CJ "Storage info" client — warehouse detail by {@code ?id=<storageId>} (GET).
 * Hits {@code spring.cjdropship.api.warehouse.detail-url}. Pacing/caching is the caller's
 * responsibility (see {@code CJProductService#getWarehouseDetail}). Uses a timeout-configured
 * RestTemplate ({@link CjTimedRestTemplates}).
 */
@Component
public class CJWarehouseClient extends CJRequestUtils {

    private final CJDropshippingConfig config;
    private final CJTokenService cjTokenService;

    public CJWarehouseClient(CJDropshippingConfig conf, ObjectMapper mapp, CJTokenService cjTokenService) {
        super(CjTimedRestTemplates.withDefaultTimeouts(), mapp);
        this.config = conf;
        this.cjTokenService = cjTokenService;
    }

    public CJWarehouseDetailResponse getWarehouseDetail(String storageId) {
        try {
            String accessToken = cjTokenService.getValidToken();
            String detailUrl = config.getWarehouseDetailUrl();
            if (detailUrl == null || !detailUrl.matches("^https?://.*")) {
                throw new IllegalArgumentException("Warehouse detail URL must be absolute (include http:// or https://)");
            }
            String url = UriComponentsBuilder.fromUriString(detailUrl)
                    .queryParam("id", storageId)
                    .build().toUriString();
            return makeGetRequest(url, CJWarehouseDetailResponse.class, accessToken,
                    "Failed to fetch warehouse detail");
        } catch (Exception e) {
            throw new RuntimeException("Warehouse detail fetch failed: " + e.getMessage(), e);
        }
    }
}
