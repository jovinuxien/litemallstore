package org.linlinjava.litemall.core.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "litemall.stripe")
public class StripeProperties {

    private String publishedKey;
    public  String secretKey;


    public String getPublishedKey() {
        return publishedKey;
    }

    public String getSecretKey(){
        return secretKey;
    }

    public void setPublishedKey(String publishedKey) {
        this.publishedKey = publishedKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
