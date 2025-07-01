package org.linlinjava.litemall.gateway.infrastructure.feignclient;


import com.aliyun.oss.ServiceException;
import org.linlinjava.litemall.gateway.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gateway.domain.model.services.BaseWebClientService;
import org.linlinjava.litemall.gateway.domain.valueobjects.user.ApiResponse;
import org.slf4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashSet;

@Service
public class LitemallFeignUserClient extends BaseWebClientService {


    private static final String USER_DETAIL_PATH = "/srv/account";
    public static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LitemallFeignUserClient.class);

    public LitemallFeignUserClient(WebClient webClient) {
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

    public Mono<LitemallUserAggregate> getUserDetailByUsername(String username, String token) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(serviceBasePath + "/detailByUsername")
                        .queryParam("username", username)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new ServiceException(
                                        "Failed to fetch user details: " + error
                                        //response.statusCode()
                                )))
                )
                .onStatus(HttpStatus::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new ServerErrorException(
                                        "Server error fetching user: " + body
                                )))
                )
                //.bodyToMono(new ParameterizedTypeReference<ApiResponse<LitemallUserAggregate>>() {})
                .bodyToMono(LitemallUserAggregate.class)
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100)));
               // .map(ApiResponse::getData);
    }


    public Mono<LitemallUserAggregate> createUser(String username, String token) {
        LitemallUserAggregate newUser = new LitemallUserAggregate();
        newUser.setUsername(username);
        newUser.setAdmin(false);
        //return handleResponse(

              return  webClient.post()
                        .uri(serviceBasePath + "/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "+ token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(newUser)
                        .retrieve()
                        .onStatus(HttpStatus::isError, response ->
                                  response.bodyToMono(String.class)
                                          .flatMap(body -> Mono.error(new ServiceException(
                                                  "Error creating user: " + body
                                                  //response.statusCode()
                                          )))
                        )
                      .bodyToMono(LitemallUserAggregate.class)
                      .timeout(Duration.ofSeconds(5))
                      .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));
    }

    public Mono<Void> createAccount(LitemallUserAggregate user, String token) {
        return webClient.post()
                .uri(serviceBasePath + "/account/create")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(user)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .then();
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
