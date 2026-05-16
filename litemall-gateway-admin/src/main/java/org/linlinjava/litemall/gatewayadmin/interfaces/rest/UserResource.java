package org.linlinjava.litemall.gatewayadmin.interfaces.rest;


import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.linlinjava.litemall.gatewayadmin.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gatewayadmin.infrastructure.feignclient.LitemallFeignUserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/srv")
public class UserResource {

    @Autowired
    private WebClient webClient;
    @Autowired
    private LitemallFeignUserClient feignUserClient;

    private final Logger log = LoggerFactory.getLogger(UserResource.class);


    //Try to create a new user
    @GetMapping("/register")
    public Mono<ResponseEntity<LitemallUserAggregate>> saveFromAuthenticationUser(
            //@AuthenticationPrincipal Mono<Authentication> authenticationMono){
            @AuthenticationPrincipal Mono<Jwt> jwtMono){

           /* return
                authenticationMono
                .flatMap(auth -> Mono.zip(
                        extractToken(auth),
                        extractPrincipal2(auth)
                ))
                .flatMap(tuple -> {
                    String token = tuple.getT1();
                    String username = tuple.getT2();



                    // Fetch existing user details if needed
                    return feignUserClient.getUserDetailByUsername(username, token)
                            //.flatMap(userAggregate -> feignUserClient.createUser(userAggregate, token))
                            .switchIfEmpty(Mono.defer(() -> createNewUser(username, token)))
                            .flatMap(userAggregate -> feignUserClient.createUser(userAggregate,token));
                })
                .map(ResponseEntity::ok)
                .onErrorResume(ServiceException.class, ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()))
                .onErrorResume(ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));

                */
        return jwtMono
                .flatMap(jwt -> {
                    String username = jwt.getClaimAsString("preferred_username");
                    String token = jwt.getTokenValue();

                    // 1. First try to fetch user
                    return feignUserClient.getUserDetailByUsername(username, token)
                            // 2. If not found, create new user
                            .switchIfEmpty(Mono.defer(() -> feignUserClient.createUser(username, token)))
                            // 3. Then process the user
                            .flatMap(user -> {
                                // Add any additional processing here
                                return Mono.just(user);
                            });
                })
                .map(ResponseEntity::ok)
                .onErrorResume(WebClientResponseException.class, ex ->
                        Mono.just(ResponseEntity.status(ex.getStatusCode()).build()))
                .onErrorResume(ex ->
                        Mono.just(ResponseEntity.internalServerError().build()))
                .doOnSubscribe(sub -> log.info("Starting user fetch/save process"))
                .doOnSuccess(res -> log.info("Successfully processed user"))
                .doOnError(err -> log.error("Error processing user", err));
    }

    /**
     *
     * @param credentials
     * @desc: Authentication with keycloak not with local database
     */

    @PostMapping("/authenticate")
    public Mono<ResponseEntity<Map<String, Object>>> login(@RequestBody Map<String, String> credentials) {
        // Create form data
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", "web_app");
        formData.add("username", credentials.get("username"));
        formData.add("password", credentials.get("password"));
        formData.add("client_secret", "web_app"); // If client is confidential

        return WebClient.create()
                .post()
                .uri("http://localhost:9080/realms/jhipster/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    // Log detailed error from Keycloak
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                System.err.println("Keycloak error response: " + body);
                                return Mono.error(new RuntimeException(body));
                            });
                })
                .bodyToMono(Map.class)
                .map(response -> ResponseEntity.ok(Map.of(
                        "token", response.get("access_token"),
                        "refreshToken", response.get("refresh_token"),
                        "expiresIn", response.get("expires_in")
                )))
                .onErrorResume(e -> {
                        System.err.println("Authentication error: " + e.getMessage());
                         return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "Authentication failed",
                        "details", e.getMessage())));
                });

    }

}
