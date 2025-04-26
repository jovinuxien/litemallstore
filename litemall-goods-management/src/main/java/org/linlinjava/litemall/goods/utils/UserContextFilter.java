package org.linlinjava.litemall.goods.utils;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * This class is a filter component that sets user context information from incoming HTTP requests.
 * It retrieves the correlation ID, user ID, authentication token, and goods service ID from the request headers
 * and sets them in the UserContextHolder for further use within the application.
 *
 * @author
 */
@Component
public class UserContextFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(UserContextFilter.class);


    /**
     * Initializes the filter.
     *
     * @param filterConfig the filter configuration
     * @throws ServletException if an error occurs during initialization
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }


    /**
     * Processes the incoming HTTP request and sets user context information.
     *
     * @param request the incoming HTTP request
     * @param response the HTTP response
     * @param chain the filter chain
     * @throws IOException if an input/output error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        UserContextHolder.getContext().setCorrelationId(  httpServletRequest.getHeader(UserContext.CORRELATION_ID) );
        UserContextHolder.getContext().setUserId( httpServletRequest.getHeader(UserContext.USER_ID) );
        UserContextHolder.getContext().setAuthToken( httpServletRequest.getHeader(UserContext.AUTH_TOKEN) );
        UserContextHolder.getContext().setGoodsServiceId(httpServletRequest.getHeader(UserContext.ORDER_SERVICE_ID) );

        logger.debug("Organization Service Incoming Correlation id: {}" ,UserContextHolder.getContext().getCorrelationId());
        chain.doFilter(httpServletRequest, response);
    }

    /**
     * Destroys the filter.
     */
    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
