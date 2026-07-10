package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CJ-side tracking summary for one tracking number ({@code logistic/trackInfo}). CJ reports
 * summary level only (current status + latest-event time), not a per-hop event list.
 */
@Data
@AllArgsConstructor
public class CjTrackingSnapshot {
    private String trackingNumber;
    private String carrier;
    /** Origin country code. */
    private String origin;
    /** Destination country code. */
    private String destination;
    private String deliveryDay;
    /** Timestamp of the latest tracking event, "YYYY-MM-DD HH:MM:SS". */
    private String deliveryTime;
    /** Current shipment status, e.g. "In transit". */
    private String trackingStatus;
    private String lastMileCarrier;
    private String lastTrackNumber;
}
