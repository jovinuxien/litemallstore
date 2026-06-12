package org.linlinjava.litemall.order.interfaces.dtos.cj;

import lombok.Data;

import java.util.List;

/**
 * Request body for {@code POST /srv/order/cj/orders} — the callable CJ-order contract. The
 * (future) checkout-routing calls {@code CjDropshipOrderFacade} directly instead of this endpoint.
 */
@Data
public class CjOrderRequestDto {
    private String orderNumber;
    private String customerName;
    private String phone;
    private String countryCode;
    private String country;
    private String province;
    private String city;
    private String address;
    private String zip;
    private String remark;
    private List<Line> lines;

    @Data
    public static class Line {
        private String vid;
        private int quantity;
    }
}
