package org.linlinjava.litemall.goods.utils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Per-request filter that reads the trusted identity headers injected by
 * litemall-gateway-admin's {@code IdentityForwardingFilter} (X-User-Id,
 * X-User-Type, X-User-Roles) and stashes them on {@link UserContext} for the
 * downstream service code to read. The legacy {@code UserContextInterceptor}
 * is removed — there is exactly one ingestion point.
 */
@Component
public class UserContextFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserContextFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        try {
            UserContext.setUserId(http.getHeader(UserContext.HDR_USER_ID));
            UserContext.setUserType(http.getHeader(UserContext.HDR_USER_TYPE));
            UserContext.setUserRoles(http.getHeader(UserContext.HDR_USER_ROLES));
            UserContext.setCorrelationId(http.getHeader(UserContext.HDR_CORRELATION_ID));
            UserContext.setAuthToken(http.getHeader(UserContext.HDR_AUTH_TOKEN));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Goods request — userId={}, type={}, correlationId={}",
                        UserContext.getUserId(), UserContext.getUserType(), UserContext.getCorrelationId());
            }
            chain.doFilter(http, response);
        } finally {
            UserContext.clear();
        }
    }
}
