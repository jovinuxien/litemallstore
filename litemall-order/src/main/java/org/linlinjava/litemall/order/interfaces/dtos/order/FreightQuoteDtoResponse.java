package org.linlinjava.litemall.order.interfaces.dtos.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Checkout freight/logistics quote. {@code freightPrice} is what submit will actually charge
 * (the {@code litemall_express_freight_min/value} rule). {@code cj} is informational only —
 * carrier + delivery estimate; CJ's own shipping cost is internal and deliberately absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@AllArgsConstructor
public class FreightQuoteDtoResponse {

    /** The freight the order will be charged at submit for this cart group. */
    private final BigDecimal freightPrice;

    /** Goods subtotal at/above which shipping is free (the configured threshold). */
    private final BigDecimal freeShippingThreshold;

    /** The CJ logistics estimate for a CJ cart group; null when unavailable / not requested. */
    private final CjInfo cj;

    /** Set when a CJ quote was requested but could not be produced (outage, no lane, bad line). */
    private final String cjNote;

    @Getter
    @AllArgsConstructor
    public static class CjInfo {
        private final String logisticName;
        /** Delivery-time estimate as CJ reports it, e.g. "8-12" (days). */
        private final String logisticAging;
    }
}
