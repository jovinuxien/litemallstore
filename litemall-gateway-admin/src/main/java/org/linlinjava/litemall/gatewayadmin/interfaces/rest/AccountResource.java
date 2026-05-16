package org.linlinjava.litemall.gatewayadmin.interfaces.rest;


import com.aliyun.oss.ServiceException;
import org.linlinjava.litemall.gatewayadmin.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gatewayadmin.infrastructure.feignclient.LitemallFeignUserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.SecurityUtils.extractPrincipal2;
import static org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.SecurityUtils.extractToken;

@RestController
@RequestMapping("/srv/private/account")
public class AccountResource {

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    public AccountResource(ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        this.authorizedClientRepository = authorizedClientRepository;
    }
    @Autowired
    private LitemallFeignUserClient userService;

    private static class AccountResourceException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    @GetMapping("/detail/{userId}")
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
                    log.info("Creating response with username: {} and token: {}", username, token);
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


    @GetMapping("/token")
    public Mono<ResponseEntity<Map<String, String>>> getToken(@AuthenticationPrincipal Mono<Authentication> authenticationMono, ServerWebExchange exchange) {
        return authenticationMono
                //.doOnNext(auth -> log.info("Authentication object: {}", auth.getCredentials()))
                .flatMap(auth -> {
                    if(auth == null || !auth.isAuthenticated()){
                        Map<String, String> errorInfo = new HashMap<>();
                        errorInfo.put("error", "Unauthorized");
                        errorInfo.put("message", "You need to authenticate first");
                        return Mono.just(
                                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(errorInfo)
                        );
                    } // Case 2: OAuth2 flow
                    if (auth instanceof OAuth2AuthenticationToken oauthToken) {
                        return processOAuthToken(oauthToken, exchange);
                    }
                    // Case 3: Standard authentication
                    else {
                        return processStandardToken(auth);
                    }

                }).defaultIfEmpty(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "error", "Unauthorized",
                                        "message", "No authentication credentials provided"
                                ))
                );
    }

    private Mono<ResponseEntity<Map<String, String>>> processOAuthToken(OAuth2AuthenticationToken oauthToken, ServerWebExchange exchange) {
        return authorizedClientRepository.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken,
                        exchange
                )
                .map(authorizedClient -> {
                    OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
                    return ResponseEntity.ok(Map.of(
                            "username", oauthToken.getName(),
                            "token", accessToken.getTokenValue(),
                            "token_type", accessToken.getTokenType().getValue()
                    ));
                })
                .switchIfEmpty(Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "error", "OAuth2 token missing",
                                        "message", "Failed to load OAuth2 token"
                                ))
                ));
    }

    private Mono<ResponseEntity<Map<String, String>>> processStandardToken(Authentication auth){
        return extractToken(auth)
                .zipWith(extractPrincipal2(auth))
                .map(tuple -> ResponseEntity.ok(Map.of(
                        "username", tuple.getT2(),
                        "token", tuple.getT1()
                )))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "error", "Token extraction failed",
                                        "message", e.getMessage()
                                ))
                ));
    }
}
