package org.linlinjava.litemall.gatewayadmin.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * litemall response envelope {@code {errno, errmsg, data}}.
 *
 * <p>Single home for the admin edge (de-duplicated: the former
 * {@code domain.valueobjects.user.ApiResponse} is gone). Serves both
 * directions:
 * <ul>
 *   <li><b>Outbound</b> — {@code AuthController} builds responses with the
 *       {@link #ok(Object)} / {@link #fail(int, String)} factories.</li>
 *   <li><b>Inbound</b> — {@code BaseWebClientService} deserializes downstream
 *       responses into {@code ApiResponse<T>} via a
 *       {@code ParameterizedTypeReference}.</li>
 * </ul>
 *
 * <p>Reimplemented here (instead of litemall-core's {@code ResponseUtil})
 * because litemall-core drags servlet Spring MVC into this reactive gateway.
 *
 * <p>{@code @JsonInclude(ALWAYS)} so a failure response still carries an
 * explicit {@code "data": null}, matching the litemall SPA contract.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private int errno;
    private String errmsg;
    private T data;

    public ApiResponse() {
    }

    private ApiResponse(int errno, String errmsg, T data) {
        this.errno = errno;
        this.errmsg = errmsg;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data);
    }

    public static <T> ApiResponse<T> fail(int errno, String errmsg) {
        return new ApiResponse<>(errno, errmsg, null);
    }

    public int getErrno() {
        return errno;
    }

    public void setErrno(int errno) {
        this.errno = errno;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public void setErrmsg(String errmsg) {
        this.errmsg = errmsg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
