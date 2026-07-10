package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.warehouse;

import lombok.Data;

/**
 * Envelope for CJ {@code warehouse/detail}: {@code {code:200, result:true, message, data,
 * requestId}}. A known miss is {@code code:1608001 "Warehouse info not found"} with null data —
 * callers map that to a clean errmsg, never a 5xx.
 */
@Data
public class CJWarehouseDetailResponse {
    private int code;
    private boolean result;
    private String message;
    private CJWarehouseDetail data;
    private String requestId;

    public boolean isOk() {
        return result || code == 200;
    }
}
