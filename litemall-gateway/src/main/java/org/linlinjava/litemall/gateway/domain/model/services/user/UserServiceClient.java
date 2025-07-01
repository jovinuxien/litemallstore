package org.linlinjava.litemall.gateway.domain.model.services.user;


import org.linlinjava.litemall.gateway.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gateway.infrastructure.config.security.SecurityUtils;
import org.linlinjava.litemall.gateway.infrastructure.feignclient.LitemallFeignUserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserServiceClient {

    public static final Logger LOGGER = LoggerFactory.getLogger(UserServiceClient.class);

    private final LitemallFeignUserClient litemallFeignUserClient;

    public UserServiceClient(LitemallFeignUserClient litemallFeignUserClient) {
        this.litemallFeignUserClient = litemallFeignUserClient;
    }


    /*public Mono<LitemallUserAggregate> saveUser(LitemallUserAggregate userAggregate) {
        return SecurityUtils.getCurrentUserLogin()
                .switchIfEmpty(Mono.error(new RuntimeException("User not authenticated")))
                .flatMap(userName -> {
                    userAggregate.setUsername(userName);
                    LOGGER.info("Saving user {} with username {}", userAggregate.getUserId(), userAggregate.getUsername());
                    return litemallFeignUserClient.createUser(userAggregate);
                }).then(Mono.just(userAggregate));
    }*/



}
