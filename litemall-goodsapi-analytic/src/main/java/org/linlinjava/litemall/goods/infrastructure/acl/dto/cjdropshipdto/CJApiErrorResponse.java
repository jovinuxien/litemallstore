package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto;


import lombok.Data;

@Data
public class CJApiErrorResponse {
    private Integer code;
    private boolean result;
    private String message;
    private Object data;
    private String requestId;
}
