package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj;

import org.linlinjava.litemall.order.infrastructure.configuration.FeignConfig;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjAuthRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjAuthResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * CJ Dropshipping authentication client. The order service owns its OWN CJ auth (the CJ ACL in
 * goods-management is a separate module, not a shared library). Host is config-driven via
 * {@code spring.cjdropship.api.base-url}; the path is the CJ v2 auth endpoint.
 */
@FeignClient(name = "cj-dropship-auth", url = "${spring.cjdropship.api.base-url}", configuration = FeignConfig.class)
public interface CjAuthFeignClient {

    @PostMapping("/authentication/getAccessToken")
    CjAuthResponse getAccessToken(@RequestBody CjAuthRequest request);
}
