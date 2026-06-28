package org.linlinjava.litemall.order.interfaces.dtos.order;

import lombok.Getter;

import java.util.List;

/**
 * Paged customer order list: {@code { list, total }} — the exact shape the SPA
 * {@code orderApi.list} consumes (after unwrapping the {@code ApiResponse}
 * envelope).
 */
@Getter
public class OrderListDtoResponse {

    private final List<OrderListItemDtoResponse> list;
    private final long total;

    public OrderListDtoResponse(List<OrderListItemDtoResponse> list, long total) {
        this.list = list;
        this.total = total;
    }
}
