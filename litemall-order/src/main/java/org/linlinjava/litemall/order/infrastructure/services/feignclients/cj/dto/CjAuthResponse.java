package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * CJ Dropshipping {@code getAccessToken} response envelope:
 * <pre>{ code, result, message, data:{ accessToken, accessTokenExpiryDate, refreshToken, ... }, requestId }</pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjAuthResponse {
    private int code;
    private boolean result;
    private String message;
    private Data data;
    private String requestId;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("accessToken")
        private String accessToken;
        @JsonProperty("accessTokenExpiryDate")
        private String accessTokenExpiryDate;
        @JsonProperty("refreshToken")
        private String refreshToken;
        @JsonProperty("refreshTokenExpiryDate")
        private String refreshTokenExpiryDate;
    }
}
