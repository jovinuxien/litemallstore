package org.linlinjava.litemall.gateway.interfaces.rest;


import com.google.protobuf.ServiceException;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import org.linlinjava.litemall.gateway.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gateway.infrastructure.feignclient.LitemallFeignUserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.linlinjava.litemall.gateway.infrastructure.config.security.SecurityUtils.extractPrincipal2;
import static org.linlinjava.litemall.gateway.infrastructure.config.security.SecurityUtils.extractToken;

@RestController
@RequestMapping("/srv")
public class AccountResource {

    @Autowired
    private LitemallFeignUserClient userService;

    private static class AccountResourceException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);


    @RequestMapping("/account")
    public String getAccount() {
        throw new AccountResourceException("This is a test exception");
    }


    @GetMapping("/account/detail/{userId}")
    public Mono<ResponseEntity<LitemallUserAggregate>> getUserDetail(@PathVariable("userId") String userId) {
        return userService.getUserDetail(parseUserId(userId))
                .map(ResponseEntity::ok)
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build())
                )
                .onErrorResume(ServiceException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build())
                );
    }

    @GetMapping("/user-info")
    public Mono<ResponseEntity<Map<String, String>>> getUserInfo(@AuthenticationPrincipal Mono<Authentication> authenticationMono) {
        return authenticationMono
                .doOnNext(auth -> log.info("Authentication object: {}", auth))
                .flatMap(auth -> Mono.zip(
                       // extractToken(auth).doOnNext(token -> log.info("Extracted token: {}", token)),
                        extractToken(auth),
                        //extractPrincipal2(auth).doOnNext(principal -> log.info("Extracted principal: {}", principal))
                        extractPrincipal2(auth)
                ))
                .map(tuple -> {
                    String token = tuple.getT1();
                    String username = tuple.getT2();
                    //log.info("Creating response with username: {} and token: {}", username, token);
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("username", username);
                    userInfo.put("token", token);
                    return userInfo;
                })
                .map(ResponseEntity::ok)
                //.doOnNext(response -> log.info(" The sending response: {}", response))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                //.doOnError(e -> log.error("An error in getting userInfo: {}", e.getMessage()));
    }

    private Integer parseUserId(String userId) {
        try {
            return Integer.parseInt(userId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid user ID format: " + userId
            );
        }
    }
}
