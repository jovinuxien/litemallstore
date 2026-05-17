package org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {
    private int errno;
    private T data;
    private String errmsg;
}
