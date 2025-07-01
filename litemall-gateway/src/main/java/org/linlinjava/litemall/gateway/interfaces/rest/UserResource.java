package org.linlinjava.litemall.gateway.interfaces.rest;


import org.linlinjava.litemall.gateway.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gateway.infrastructure.feignclient.LitemallFeignUserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/srv/user")
public class UserResource {

    @Autowired
    private WebClient webClient;
    @Autowired
    private LitemallFeignUserClient feignUserClient;

    private final Logger log = LoggerFactory.getLogger(UserResource.class);


    //Try to create a new user
    @GetMapping("/new-user")
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



}
