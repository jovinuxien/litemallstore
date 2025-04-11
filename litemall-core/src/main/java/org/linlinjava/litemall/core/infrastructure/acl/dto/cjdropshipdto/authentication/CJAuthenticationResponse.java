package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.authentication;


import java.time.LocalDateTime;

public class CJAuthenticationResponse {

    private Integer code;
    private Boolean result;
    private String message;
    private CjAuthResponseData data;

    public Integer getCode() {
        return code;
    }
    public void setCode(Integer code) {
        this.code = code;
    }
    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    public CjAuthResponseData getData() {
        return data;
    }
    public void setData(CjAuthResponseData data) {
        this.data = data;
    }
}
