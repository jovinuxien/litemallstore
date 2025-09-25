package org.linlinjava.litemall.goods.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * This class is an interceptor for HTTP requests that adds user context information to the request headers.
 * It implements the {@link ClientHttpRequestInterceptor} interface to intercept and modify outgoing HTTP requests.
 */
public class UserContextInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(UserContextInterceptor.class);


    /**
     * Intercepts an outgoing HTTP request and adds user context information to the request headers.
     *
     * @param request The outgoing HTTP request.
     * @param body The request body as a byte array.
     * @param execution The execution context for the HTTP request.
     * @return The response to the intercepted HTTP request.
     * @throws IOException If an I/O error occurs during the request execution.
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        headers.add(UserContext.CORRELATION_ID, UserContextHolder.getContext().getCorrelationId());
        headers.add(UserContext.AUTH_TOKEN, UserContextHolder.getContext().getAuthToken());


        return execution.execute(request, body);
    }
}
