package org.linlinjava.litemall.core.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class CJDropshippingConfig {

    @Value("${litemall.cjdropship.api.auth.cj-email}")
    private String cjEmail;

    @Value("${litemall.cjdropship.api.auth.cj-apiKey}")
    private String cjApiKey;

    @Value("${litemall.cjdropship.api.auth.access-url}")
    private String accessUrl;

    @Value("${litemall.cjdropship.api.auth.refresh-url}")
    private String refreshUrl;

    @Value("${litemall.cjdropship.api.product.list-url}")
    private String productListUrl;

    @Value("${litemall.cjdropship.api.product.product-detail-url}")
    private String productDetailUrl;

}
