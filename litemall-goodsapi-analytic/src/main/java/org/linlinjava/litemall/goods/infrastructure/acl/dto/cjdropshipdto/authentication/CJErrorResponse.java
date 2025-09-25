package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication;

public class CJErrorResponse {

    private int code;
    private boolean result;
    private String message;
    private Object data;
    private String requestId;


    public CJErrorResponse(){

    }
    public CJErrorResponse(int code, boolean result, String message, Object data, String requestId) {
        this.code = code;
        this.result = result;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

