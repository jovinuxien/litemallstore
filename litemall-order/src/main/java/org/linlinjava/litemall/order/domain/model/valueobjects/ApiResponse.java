package org.linlinjava.litemall.order.domain.model.valueobjects;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {
    private int errno;
    private T data;
    private String errmsg;

    /** Success envelope: {@code errno = 0} with the given payload. */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.errno = 0;
        r.data = data;
        return r;
    }

    /** Failure envelope: a non-zero {@code errno} with a human-readable message. */
    public static <T> ApiResponse<T> fail(int errno, String errmsg) {
        ApiResponse<T> r = new ApiResponse<>();
        r.errno = errno;
        r.errmsg = errmsg;
        return r;
    }
}
