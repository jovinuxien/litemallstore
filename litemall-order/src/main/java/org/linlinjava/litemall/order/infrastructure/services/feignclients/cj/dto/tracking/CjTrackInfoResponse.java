package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.tracking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * CJ Dropshipping {@code logistic/trackInfo} response envelope:
 * <pre>{ code, result, message, data:[ { trackingNumber, logisticName, trackingFrom,
 *   trackingTo, deliveryDay, deliveryTime, trackingStatus, lastMileCarrier,
 *   lastTrackNumber } ], requestId }</pre>
 * The endpoint is batch-capable (repeated {@code trackNumber} params), hence the array;
 * CJ reports summary-level tracking (no per-hop event array).
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
        private String lastTrackNumber;
    }
}
