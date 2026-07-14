package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A missing {@code X-User-Id} means the gateway forwarded the request WITHOUT a verified
 * customer identity: the customer JWT was absent or stale (e.g. minted before a gateway
 * restart on the old ephemeral keypair) and {@code IdentityForwardingFilter} strips
 * client-supplied identity headers, so nothing was re-injected. That is a session problem,
 * not a server fault — answer the standard unlogin envelope (errno 501) so the SPA
 * re-prompts for login, instead of the 500 + MissingRequestHeaderException stack trace.
 *
 * <p>Lives here (not in litemall-core's GlobalExceptionHandler): core still compiles
 * against the javax-era servlet stack, where MissingRequestHeaderException's supertypes
 * are unresolvable.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MissingIdentityAdvice {

    private static final Logger log = LoggerFactory.getLogger(MissingIdentityAdvice.class);

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseBody
    public Object missingHeader(MissingRequestHeaderException e) {
        if ("X-User-Id".equalsIgnoreCase(e.getHeaderName())) {
            log.warn("Request without X-User-Id (stale/absent customer JWT at the gateway) — answering unlogin");
            return ResponseUtil.unlogin();
        }
        log.warn("Missing request header {}", e.getHeaderName());
        return ResponseUtil.badArgument();
    }
}
