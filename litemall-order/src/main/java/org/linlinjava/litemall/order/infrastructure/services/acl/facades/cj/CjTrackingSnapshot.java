package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.Data;

import java.util.List;
import java.util.Locale;

/**
 * CJ-side tracking for one tracking number ({@code logistic/trackInfo}): the summary
 * (current status, carrier, route) plus the per-hop history and the last-mile carrier's
 * contact details when CJ supplies them.
 */
@Data
public class CjTrackingSnapshot {
    private String trackingNumber;
    private String carrier;
    /** Origin country code. */
    private String origin;
    /** Destination country code. */
    private String destination;
    private String deliveryDay;
    /** Timestamp of the latest tracking event, "YYYY-MM-DD HH:MM:SS"; often absent. */
    private String deliveryTime;
    /** Current shipment status, e.g. "In transit". */
    private String trackingStatus;
    private String lastMileCarrier;
    private String lastTrackNumber;
    private String lastMileCarrierPhone;
    private String lastMileCarrierEmail;
    /** Per-hop history, newest first; never null. */
    private List<Hop> hops = List.of();

    /** One tracking event: when, where (may be blank) and what happened in CJ's words. */
    public record Hop(String time, String location, String remark) {
    }

    public CjTrackingSnapshot(String trackingNumber, String carrier, String origin, String destination,
                              String deliveryDay, String deliveryTime, String trackingStatus,
                              String lastMileCarrier, String lastTrackNumber) {
        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.origin = origin;
        this.destination = destination;
        this.deliveryDay = deliveryDay;
        this.deliveryTime = deliveryTime;
        this.trackingStatus = trackingStatus;
        this.lastMileCarrier = lastMileCarrier;
        this.lastTrackNumber = lastTrackNumber;
    }

    public void setHops(List<Hop> hops) {
        this.hops = hops == null ? List.of() : List.copyOf(hops);
    }

    /**
     * Whether CJ says the parcel reached the customer. Deliberately narrow: the status (or,
     * failing that, the newest hop) must START with "delivered" — "Out for delivery",
     * "Delivery attempted" and "Undelivered" are not deliveries. A miss only delays the
     * automatic close; a false hit would start the return window on a parcel in transit.
     */
    public boolean isDelivered() {
        if (saysDelivered(trackingStatus)) {
            return true;
        }
        return !hops.isEmpty() && saysDelivered(hops.get(0).remark());
    }

    private static boolean saysDelivered(String text) {
        return text != null && text.trim().toLowerCase(Locale.ROOT).startsWith("delivered");
    }
}
