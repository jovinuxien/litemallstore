package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CJ-side view of an order as reported by {@code getOrderDetail} — the status-sync poll's input.
 * {@code orderStatus} is CJ's enum (CREATED / IN_CART / UNPAID / UNSHIPPED / SHIPPED /
 * DELIVERED / CANCELLED); {@code trackNumber} appears once the parcel ships.
 */
@Data
@AllArgsConstructor
public class CjOrderSnapshot {
    private String cjOrderId;
    private String orderStatus;
    private String subStatus;
    private String trackNumber;
    private String trackingProvider;
    private String logisticName;
}
