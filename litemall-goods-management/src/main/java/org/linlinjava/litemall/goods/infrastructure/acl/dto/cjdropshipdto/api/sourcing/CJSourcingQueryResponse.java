package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;

/**
 * Envelope for CJ {@code product/sourcing/query}. The doc example shows a single object in
 * {@code data} even though the request takes an array of sourceIds, so the field accepts a
 * bare object as a one-element list ({@code ACCEPT_SINGLE_VALUE_AS_ARRAY}).
 */
@Data
public class CJSourcingQueryResponse {
    private int code;
    private boolean result;
    private Boolean success;
    private String message;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<CJSourcingQueryItem> data;
    private String requestId;

    public boolean isOk() {
        return result || Boolean.TRUE.equals(success) || code == 200 || code == 0;
    }
}
