package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.tracking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * CJ Dropshipping {@code logistic/trackInfo} response envelope:
 * <pre>{ code, result, message, data:[ { trackingNumber, logisticName, trackingFrom,
 *   trackingTo, deliveryDay, trackingStatus, lastMileCarrier, lastMileCarrierPhone,
 *   lastMileCarrierEmail, lastTrackNumber,
 *   routes:[ { acceptAddress, acceptTime, remark } ] } ], requestId }</pre>
 * The endpoint is batch-capable (repeated {@code trackNumber} params), hence the array.
 * {@code routes} is the per-hop history, NEWEST FIRST (shape verified against a live
 * response 2026-11-21; {@code deliveryTime} was absent from that payload).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjTrackInfoResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private List<TrackInfo> data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackInfo {
        private String trackingNumber;
        private String logisticName;
        /** Origin country code. */
        private String trackingFrom;
        /** Destination country code. */
        private String trackingTo;
        private String deliveryDay;
        /** Timestamp of the latest tracking event, "YYYY-MM-DD HH:MM:SS". */
        private String deliveryTime;
        /** Current shipment status, e.g. "In transit". */
        private String trackingStatus;
        private String lastMileCarrier;
        private String lastMileCarrierPhone;
        private String lastMileCarrierEmail;
        private String lastTrackNumber;
        /** Per-hop history, newest first. Null or empty when CJ has none yet. */
        private List<Route> routes;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Route {
        /** Where the event happened; free text ("HEL", "CHINA"), may be blank. */
        private String acceptAddress;
        /** "YYYY-MM-DD HH:MM:SS". */
        private String acceptTime;
        /** What happened, in CJ's words ("Released from customs"). */
        private String remark;
    }
}
