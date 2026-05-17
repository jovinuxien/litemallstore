package org.linlinjava.litemall.loyalty.infrastructure.services.feignclients.exception;

import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            if (response.body() != null) {
                String body = Util.toString(response.body().asReader());
                return new RuntimeException("Feign client error [" + methodKey + "]: " + body);
            }
        } catch (IOException e) {
            log.error("Failed to process Feign response body", e);
        }
        return new RuntimeException("Feign client error [" + methodKey + "] without body, status: " + response.status());
    }
}
