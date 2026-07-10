package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing;

import lombok.Data;

/**
 * Envelope for CJ {@code product/sourcing/create}. The docs show a {@code success/code:0}
 * envelope while the live API2 answers {@code code:200/result:true} like the other endpoints,
 * so both flags are mapped; use {@link #isOk()} rather than reading one directly.
 */
@Data
public class CJSourcingCreateResponse {
    private int code;
    private boolean result;
    private Boolean success;
    private String message;
    private CJSourcingCreateData data;
    private String requestId;

    public boolean isOk() {
        return result || Boolean.TRUE.equals(success) || code == 200 || code == 0;
    }
}
