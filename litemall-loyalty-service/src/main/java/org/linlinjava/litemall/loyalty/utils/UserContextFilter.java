package org.linlinjava.litemall.loyalty.utils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UserContextFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(UserContextFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        UserContextHolder.getContext().setCorrelationId(httpRequest.getHeader(UserContext.CORRELATION_ID));
        UserContextHolder.getContext().setUserId(httpRequest.getHeader(UserContext.USER_ID));
        UserContextHolder.getContext().setAuthToken(httpRequest.getHeader(UserContext.AUTH_TOKEN));
        UserContextHolder.getContext().setLoyaltyServiceId(httpRequest.getHeader(UserContext.LOYALTY_SERVICE_ID));

        logger.debug("Loyalty Service Incoming Correlation id: {}",
                UserContextHolder.getContext().getCorrelationId());

        chain.doFilter(httpRequest, response);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
