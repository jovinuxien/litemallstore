package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo;

import lombok.Data;

import java.util.List;

/** Envelope for CJ {@code product/queryVideosByProductId}; {@code data} is the video list. */
@Data
public class CJProductVideoResponse {
    private int code;
    private boolean result;
    private Boolean success;
    private String message;
    private List<CJProductVideo> data;
    private String requestId;

    public boolean isOk() {
        return result || Boolean.TRUE.equals(success) || code == 200 || code == 0;
    }
}
