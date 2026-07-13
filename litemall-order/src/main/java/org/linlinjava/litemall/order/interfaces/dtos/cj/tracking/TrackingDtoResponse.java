package org.linlinjava.litemall.order.interfaces.dtos.cj.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Shipment-tracking payload for {@code GET /srv/order/{orderId}/tracking} (customer) and
 * {@code GET /srv/private/admin/order/{orderId}/tracking} (admin). Contract documented in
 * docs/handoff-gateway-admin-cj-tracking.md.
 *
 * <p>Not shipped yet is a CLEAN payload ({@code shipped=false}, {@code status="NOT_SHIPPED"},
 * empty {@code events}), never an error. CJ reports summary-level tracking only, so
 * {@code events} carries a single entry synthesized from the current status + latest-event
 * time; the list shape leaves room if CJ ever exposes per-hop detail.
 */
@Data
@Builder
public class TrackingDtoResponse {

    /** Whether the order has a tracking number ({@code ship_sn}) yet. */
    private boolean shipped;
    /** {@code NOT_SHIPPED}, CJ's trackingStatus (e.g. "In transit"), or null when CJ has no data. */
    private String status;
    private String carrier;
    private String trackNumber;
    /** Origin country code (CJ orders). */
    private String origin;
    /** Destination country code (CJ orders). */
    private String destination;
    /** Estimated delivery window in days, as CJ reports it. */
    private String deliveryDay;
    private String lastMileCarrier;
    private String lastTrackNumber;
    private List<Event> events;
    /**
     * Advisory hint for LOCAL orders only (Wave 4): {@code "tracking provider disabled"} or
     * {@code "tracking temporarily unavailable"}. Never set on CJ payloads. FIELD-level
     * NON_NULL on purpose: the class serializes nulls today, and keeping this the only
     * conditionally-present field leaves ALL pre-Wave-4 payloads byte-identical.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String note;

    @Data
    @AllArgsConstructor
    public static class Event {
        /** "YYYY-MM-DD HH:MM:SS" (CJ's latest-event timestamp). */
        private String time;
        private String status;
        private String description;
    }
}
