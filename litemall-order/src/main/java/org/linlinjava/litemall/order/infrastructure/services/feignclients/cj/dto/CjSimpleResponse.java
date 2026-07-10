package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * CJ envelope for operations whose {@code data} is a bare scalar (usually the echoed orderId):
 * {@code confirmOrder}, {@code deleteOrder}, {@code payBalance}. Success is decided from
 * {@code result}/{@code code} only, mirroring {@code CjCreateOrderResponse} semantics.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjSimpleResponse {
    private int code;
    private boolean result;
    private String message;
    private String data;
    private String requestId;
}
