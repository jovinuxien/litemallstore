package org.linlinjava.litemall.gateway.domain.services.user;


import com.aliyun.oss.ServiceException;
import org.linlinjava.litemall.gateway.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gateway.domain.services.BaseWebClientService;
import org.linlinjava.litemall.gateway.domain.valueobjects.user.ApiResponse;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class LitemallUserService extends BaseWebClientService {


    private static final String USER_DETAIL_PATH = "/srv/account";
    public static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LitemallUserService.class);

    public LitemallUserService(WebClient webClient) {
       super(webClient, USER_DETAIL_PATH);
    }

    public Mono<LitemallUserAggregate> getUserDetail(Integer userId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(serviceBasePath + "/detail")
                        .queryParam("id", userId)
                        .build())
                .retrieve()
                .onStatus(HttpStatus::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new ServiceException(
                                        "Failed to fetch user details: " + error
                                        //response.statusCode()
                                )))
                )
                .bodyToMono(responseTypeRef(LitemallUserAggregate.class))
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
                .map(ApiResponse::getData);
    }

    public Mono<LitemallUserAggregate> createUser(LitemallUserAggregate user) {
        return handleResponse(
                webClient.post()
                        .uri(serviceBasePath + "/create")
                        .bodyValue(user)
                        .retrieve()
                        .bodyToMono(responseTypeRef(LitemallUserAggregate.class))
                        .map(ApiResponse::getData)
        );
    }

    public Mono<Void> updateUser(Integer userId, LitemallUserAggregate user) {
        return handleResponse(
                webClient.put()
                        .uri(uriBuilder -> uriBuilder
                                .path(serviceBasePath + "/update")
                                .queryParam("id", userId)
                                .build())
                        .bodyValue(user)
                        .retrieve()
                        .toBodilessEntity()
                        .then()
        );
    }

    public Mono<Void> deleteUser(Integer userId) {
        return handleResponse(
                webClient.delete()
                        .uri(uriBuilder -> uriBuilder
                                .path(serviceBasePath + "/delete")
                                .queryParam("id", userId)
                                .build())
                        .retrieve()
                        .toBodilessEntity()
                        .then()
        );
    }
}
