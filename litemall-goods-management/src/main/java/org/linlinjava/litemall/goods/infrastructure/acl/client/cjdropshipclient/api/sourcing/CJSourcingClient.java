package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.sourcing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingCreateRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingCreateResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingQueryRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingQueryResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CJRequestUtils;
import org.linlinjava.litemall.goods.infrastructure.acl.utils.CjTimedRestTemplates;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CJ "Product Sourcing" client — create a sourcing request / query sourcing status.
 * Hits {@code spring.cjdropship.api.product.sourcing-create-url} / {@code sourcing-query-url}.
 * Pacing is the caller's responsibility (see {@code CJProductService}). Uses a timeout-configured
 * RestTemplate ({@link CjTimedRestTemplates}) — new CJ calls must never hang indefinitely.
 */
@Component
public class CJSourcingClient extends CJRequestUtils {

    private final CJDropshippingConfig config;
    private final CJTokenService cjTokenService;

    public CJSourcingClient(CJDropshippingConfig conf, ObjectMapper mapp, CJTokenService cjTokenService) {
        super(CjTimedRestTemplates.withDefaultTimeouts(), mapp);
        this.config = conf;
        this.cjTokenService = cjTokenService;
    }

    public CJSourcingCreateResponse createSourcing(CJSourcingCreateRequest request) {
        try {
            String accessToken = cjTokenService.getValidToken();
            String url = requireAbsolute(config.getSourcingCreateUrl(), "Sourcing create");
            return makePostRequest(url, request, CJSourcingCreateResponse.class, accessToken,
                    "Failed to create sourcing request");
        } catch (Exception e) {
            throw new RuntimeException("Sourcing create failed: " + e.getMessage(), e);
        }
    }

    public CJSourcingQueryResponse querySourcing(List<String> sourceIds) {
        try {
            String accessToken = cjTokenService.getValidToken();
            String url = requireAbsolute(config.getSourcingQueryUrl(), "Sourcing query");
            CJSourcingQueryRequest request = new CJSourcingQueryRequest();
            request.setSourceIds(sourceIds);
            return makePostRequest(url, request, CJSourcingQueryResponse.class, accessToken,
                    "Failed to query sourcing status");
        } catch (Exception e) {
            throw new RuntimeException("Sourcing query failed: " + e.getMessage(), e);
        }
    }

    private static String requireAbsolute(String url, String what) {
        if (url == null || !url.matches("^https?://.*")) {
            throw new IllegalArgumentException(what + " URL must be absolute (include http:// or https://)");
        }
        return url;
    }
}
