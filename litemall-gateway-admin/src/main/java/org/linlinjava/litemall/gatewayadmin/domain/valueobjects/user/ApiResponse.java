package org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user;


import lombok.Getter;
import lombok.Setter;

/**
 * litemall {@code {errno, errmsg, data}} envelope shared by edge-issued
 * responses (e.g. AuthController) and BFF-deserialized downstream responses
 * (e.g. BaseWebClientService).
 */
@Getter
@Setter
public class ApiResponse<T> {
    private int errno;
    private T data;
    private String errmsg;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setErrno(0);
        r.setErrmsg("成功");
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> fail(int errno, String errmsg) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setErrno(errno);
        r.setErrmsg(errmsg);
        return r;
    }
}
