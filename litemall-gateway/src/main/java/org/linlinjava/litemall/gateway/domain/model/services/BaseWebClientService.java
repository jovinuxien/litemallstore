package org.linlinjava.litemall.gateway.domain.model.services;

import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.gateway.domain.valueobjects.user.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

public abstract class BaseWebClientService {

    protected final WebClient webClient;
    protected final String serviceBasePath;

    protected BaseWebClientService(WebClient webClient, String serviceBasePath) {
        this.webClient = webClient;
        this.serviceBasePath = serviceBasePath;
    }

    protected <T> Mono<T> handleResponse(Mono<T> responseMono) {
        return responseMono
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                .onErrorMap(WebClientResponseException.class, ex ->
                        new ServiceException("Service call failed: " + ex.getResponseBodyAsString())
                );
    }

    // Helper method to create ParameterizedTypeReference
    protected <T> ParameterizedTypeReference<ApiResponse<T>> responseTypeRef(Class<T> clazz) {
        return new ParameterizedTypeReference<ApiResponse<T>>() {};
    }
}
