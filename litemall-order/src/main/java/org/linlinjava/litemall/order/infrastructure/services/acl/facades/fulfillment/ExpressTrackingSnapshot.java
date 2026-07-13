package org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Provider-neutral live-tracking answer for one local shipment (Wave 4, Task D). Both the
 * kdniao ({@code ExpressService}) and OnePass adapters map into this shape; the tracking
 * read maps it onward into {@code TrackingDtoResponse.Event}s. All Strings — providers
 * disagree on time formats and status vocabularies, so no parsing happens at this seam.
 */
@Data
@AllArgsConstructor
public class ExpressTrackingSnapshot {

    private String carrier;
    private String trackNumber;
    /** Provider's current-status text (e.g. kdniao State code mapped, or OnePass status). */
    private String status;
    private List<Event> events;

    @Data
    @AllArgsConstructor
    public static class Event {
        private String time;
        private String status;
        private String description;
    }
}
