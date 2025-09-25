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

    private Api api;


    @Data
    public static class Api {
        private Auth auth;
        private Category category;
        private Product product;
    }


    @Data
    public static class Auth {
        private String cjEmail;
        private String cjApiKey;
        private String accessUrl;
        private String refreshUrl;
    }

    @Data
    public static class Category {
        private String categoryListUrl;
    }

    @Data
    public static class Product {
        private String listUrl;
        private String productDetailUrl;
    }

    // Helper method for easy access to commonly used properties
    public String getCjEmail(){
       return api != null && api.getAuth()!= null ? api.getAuth().getCjEmail() : null;
    }
    public String getCjApiKey(){
       return api != null && api.getAuth()!= null ? api.getAuth().getCjApiKey() : null;
    }

    public String getAccessUrl(){
       return api != null && api.getAuth()!= null ? api.getAuth().getAccessUrl() : null;
    }

    public String getRefreshUrl(){
       return api != null && api.getAuth()!= null ? api.getAuth().getRefreshUrl() : null;
    }

    public String getCategoryListUrl(){
       return api != null && api.getCategory()!= null ? api.getCategory().getCategoryListUrl() : null;
    }

    public String getProductListUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getListUrl() : null;
    }

    public String getProductDetailUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getProductDetailUrl() : null;
    }

}
