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
 * empty {@code events}), never an error. For CJ orders {@code events} is CJ's per-hop
 * history, newest first; when CJ has no history yet it is a single entry synthesized from
 * the current status. {@code trackingUrl}, the last-mile contact fields and
 * {@code Event.location} are additive and omitted when unknown.
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
    /** Last-mile carrier's customer-service phone, as CJ reports it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String lastMileCarrierPhone;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String lastMileCarrierEmail;
    /** External page where the customer can follow the parcel themselves. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String trackingUrl;
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
        /** "YYYY-MM-DD HH:MM:SS"; null when the carrier gave no time. */
        private String time;
        private String status;
        private String description;
        /** Where it happened, in the carrier's words ("HEL"); omitted when unknown. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String location;

        public Event(String time, String status, String description) {
            this(time, status, description, null);
        }
    }
}
