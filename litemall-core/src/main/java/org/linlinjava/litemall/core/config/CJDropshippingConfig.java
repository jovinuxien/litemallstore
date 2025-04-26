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

    @Value("${spring.cjdropship.api.auth.cj-email}")
    private String cjEmail;

    @Value("${spring.cjdropship.api.auth.cj-apiKey}")
    private String cjApiKey;

    @Value("${spring.cjdropship.api.auth.access-url}")
    private String accessUrl;

    @Value("${spring.cjdropship.api.auth.refresh-url}")
    private String refreshUrl;

    @Value("${spring.cjdropship.api.product.list-url}")
    private String productListUrl;

    @Value("${spring.cjdropship.api.product.product-detail-url}")
    private String productDetailUrl;

}
