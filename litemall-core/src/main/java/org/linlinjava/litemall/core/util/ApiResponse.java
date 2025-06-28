package org.linlinjava.litemall.core.util;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ApiResponse<T>{

    private int errno;
    private T data;
    private String errmsg;


    // Static factory methods
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setErrno(0);
        response.setData(data);
        response.setErrmsg("success");
        return response;
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> fail(int errno, String errmsg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setErrno(errno);
        response.setErrmsg(errmsg);
        return response;
    }
}
