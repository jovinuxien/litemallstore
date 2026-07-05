package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One CJ logistics line chosen from {@code freightCalculate} for a product/destination
 * combination: the carrier name, CJ's shipping cost and the delivery-time estimate.
 */
@Data
@AllArgsConstructor
public class CjLogisticsOption {
    private String logisticName;
    /** CJ's own shipping cost (USD) — internal merchant cost, never surfaced to customers. */
    private BigDecimal logisticPrice;
    /** Delivery-time estimate as CJ reports it, e.g. "8-12" (days). */
    private String logisticAging;
}
