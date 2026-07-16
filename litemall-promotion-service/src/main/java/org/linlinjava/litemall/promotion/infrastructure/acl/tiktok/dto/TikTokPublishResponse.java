package org.linlinjava.litemall.promotion.infrastructure.acl.tiktok.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * TikTok Content Posting API envelope: every response carries {@code data}
 * plus an {@code error} block whose code is the literal {@code "ok"} on
 * success (TikTok returns 200 for many logical failures — the code, not the
 * HTTP status, is the truth).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TikTokPublishResponse {

    private Data data;
    private Error error;

    public boolean isOk() {
        return error == null || "ok".equalsIgnoreCase(error.getCode());
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        @JsonProperty("publish_id")
        private String publishId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {

        private String code;
        private String message;

        @JsonProperty("log_id")
        private String logId;
    }
}
