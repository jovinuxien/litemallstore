package org.linlinjava.litemall.order.infrastructure.services.feignclients.exception;

import com.google.protobuf.ServiceException;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String s, Response response) {
        try {
            if (response.body() != null) {
                String body = Util.toString(response.body().asReader());
                return new ServiceException("Feign client error: " + body);
            }
        } catch (IOException e) {
            log.error("Failed to process response body", e);
        }
        return new ServiceException("Feign client error without body");
    }
}
