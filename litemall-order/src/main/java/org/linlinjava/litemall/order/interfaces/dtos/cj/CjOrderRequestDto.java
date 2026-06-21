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
        /**
         * Native {@code litemall_goods_product.id} of the CJ line. Preferred: the server recovers the
         * CJ variant id ({@code cj_vid}) off the row and confirms the parent goods is {@code source='cj'}
         * — no {@code cj_<pid>} parsing, no {@code vid}-as-productId hack on the caller.
         */
        private Integer productId;
        /** Optional fallback CJ variant id for internal callers that already hold it (else recovered from productId). */
        private String vid;
        private int quantity;
    }
}
