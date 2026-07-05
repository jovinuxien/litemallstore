package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Envelope for CJ dispute endpoints whose {@code data} is a bare boolean (create, cancel). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjDisputeBooleanResponse {
    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private Boolean data;
}
