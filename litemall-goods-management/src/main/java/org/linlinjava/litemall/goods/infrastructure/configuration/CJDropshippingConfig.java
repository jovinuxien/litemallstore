package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Component
@Data
@ConfigurationProperties(prefix = "spring.cjdropship")
public class CJDropshippingConfig {

    private String cjEmail;

    //@Value("${cjdropship.api.auth.cj-apiKey}")
    private String cjApiKey;

    //@Value("${cjdropship.api.auth.access-url}")
    private String accessUrl;

    //@Value("${cjdropship.api.auth.refresh-url}")
    private String refreshUrl;

    //@Value("${cjdropship.api.product.list-url}")
    private String productListUrl;

    //@Value("${cjdropship.api.product.product-detail-url}")
    private String productDetailUrl;


}
