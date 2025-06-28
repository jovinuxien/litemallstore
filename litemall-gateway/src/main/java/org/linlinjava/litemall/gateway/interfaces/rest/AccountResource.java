package org.linlinjava.litemall.gateway.interfaces.rest;


import com.google.common.base.Strings;
import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.gateway.domain.model.aggregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.gateway.domain.services.user.LitemallUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/srv")
public class AccountResource {

    @Autowired
    private LitemallUserService userService;

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
