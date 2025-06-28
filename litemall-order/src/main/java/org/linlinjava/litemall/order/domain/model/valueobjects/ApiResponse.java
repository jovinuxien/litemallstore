package org.linlinjava.litemall.order.domain.model.valueobjects;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponse<T> {
    private int errno;
    private T data;
    private String errmsg;
}
