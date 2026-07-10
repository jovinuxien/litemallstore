package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal {@code { "orderId": ... }} body shared by CJ {@code shopping/order/confirmOrder}
 * and {@code shopping/pay/payBalance}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CjOrderIdRequest {
    private String orderId;
}
