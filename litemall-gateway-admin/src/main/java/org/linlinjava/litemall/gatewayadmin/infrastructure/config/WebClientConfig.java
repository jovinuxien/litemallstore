package org.linlinjava.litemall.gatewayadmin.infrastructure.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private static final String USER_SERVICE_URL = "http://localhost:8082"; // Replace with your actual service URL

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(USER_SERVICE_URL) // Or use service discovery
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
