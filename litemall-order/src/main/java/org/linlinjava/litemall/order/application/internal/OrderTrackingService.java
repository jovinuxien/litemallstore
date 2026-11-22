package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;
import org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Shipment tracking read — order-wide, not CJ-specific (renamed from {@code CjTrackingService}
 * in Wave 4, Task D). Owner-scoped for customers (an absent or foreign order returns
 * {@code null} → the controller's 404, same convention as order detail/timeline); unscoped
 * for the admin surface. An order without a tracking number yet is a clean
 * {@code NOT_SHIPPED} payload, never an error.
 *
 * <p>Live tracking comes from CJ ({@link CjTrackingFacade}, 1h cache) for CJ-fulfilled
 * orders — that branch is untouched by Wave 4. LOCALLY-fulfilled shipped orders now consult
 * the {@link ExpressQueryPort} seam (kdniao/OnePass when configured): a hit fills real
 * events; provider disabled / provider miss degrade to the previous carrier+trackNumber
 * payload plus an advisory {@code note} ("tracking provider disabled" /
 * "tracking temporarily unavailable"). CJ payloads never carry a {@code note}; they carry
 * CJ's per-hop history, the last-mile carrier's contact details and an external tracking
 * link ({@link TrackingLinkBuilder}).
 */
@Service
public class OrderTrackingService {

    static final String NOTE_PROVIDER_DISABLED = "tracking provider disabled";
    static final String NOTE_TEMPORARILY_UNAVAILABLE = "tracking temporarily unavailable";

    private final LitemallOrderRepository orderRepository;
    private final CjTrackingFacade trackingFacade;
    private final ExpressQueryPort expressQueryPort;
    private final TrackingLinkBuilder trackingLinks;

    public OrderTrackingService(LitemallOrderRepository orderRepository, CjTrackingFacade trackingFacade,
                                ExpressQueryPort expressQueryPort) {
        this(orderRepository, trackingFacade, expressQueryPort,
                new TrackingLinkBuilder(TrackingLinkBuilder.DEFAULT_TEMPLATE));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderTrackingService(LitemallOrderRepository orderRepository, CjTrackingFacade trackingFacade,
                                ExpressQueryPort expressQueryPort, TrackingLinkBuilder trackingLinks) {
        this.orderRepository = orderRepository;
        this.trackingFacade = trackingFacade;
        this.expressQueryPort = expressQueryPort;
        this.trackingLinks = trackingLinks;
    }

    /** Customer read, owner-scoped via the gateway-injected user id. Null → 404 at the edge. */
    public TrackingDtoResponse getTrackingForUser(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderAggregate order = orderRepository.findByIdAndUserId(userId, orderId);
        return order == null ? null : build(order);
    }

    /** Admin read (the edge gateway already gated ROLE_ADMIN). Null → unknown order id. */
    public TrackingDtoResponse getTrackingForAdmin(LitemallOrderId orderId) {
        return orderRepository.findById(orderId).map(this::build).orElse(null);
    }

    static final String STATUS_TRACKING_PENDING = "TRACKING_PENDING";

    private TrackingDtoResponse build(LitemallOrderAggregate order) {
        String trackNumber = order.getShipSn();
        if (!StringUtils.hasText(trackNumber)) {
            // F10: CJ can report SHIPPED before it assigns a number. The order IS shipped —
            // saying "not shipped yet" next to a status of Shipped was the contradiction.
            boolean shipped = order.getOrderStatus() == LitemallOrderStatus.SHIPPED
                    || order.getOrderStatus() == LitemallOrderStatus.DELIVERED
                    || order.getOrderStatus() == LitemallOrderStatus.AUTO_DELIVERED;
            return TrackingDtoResponse.builder()
                    .shipped(shipped)
                    .status(shipped ? STATUS_TRACKING_PENDING : "NOT_SHIPPED")
                    .carrier(order.getShipChannel())
                    .events(List.of())
                    .build();
        }
        if (order.isCjFulfilled()) {
            // CJ branch — "CJ has no data (yet) / unreachable" degrades to
            // shipped-without-events with NO note. The external link is present either
            // way: it needs only the number, so the customer can still follow the parcel
            // when CJ does not answer.
            CjTrackingSnapshot cj = trackingFacade.trackInfo(trackNumber).orElse(null);
            TrackingDtoResponse dto = cj == null
                    ? shippedWithoutEvents(order, trackNumber, null)
                    : buildFromCj(order, trackNumber, cj);
            dto.setTrackingUrl(trackingLinks.urlFor(trackNumber));
            return dto;
        }
        // Local order: consult the express seam (Wave 4, Task D).
        if (!expressQueryPort.enabled()) {
            return shippedWithoutEvents(order, trackNumber, NOTE_PROVIDER_DISABLED);
        }
        ExpressTrackingSnapshot snapshot =
                expressQueryPort.query(order.getShipChannel(), trackNumber).orElse(null);
        return snapshot == null
                ? shippedWithoutEvents(order, trackNumber, NOTE_TEMPORARILY_UNAVAILABLE)
                : buildFromExpress(order, trackNumber, snapshot);
    }

    /** Shipped, carrier + trackNumber known, no live events. {@code note} only for LOCAL orders. */
    private static TrackingDtoResponse shippedWithoutEvents(LitemallOrderAggregate order,
                                                            String trackNumber, String note) {
        return TrackingDtoResponse.builder()
                .shipped(true)
                .status(null)
                .carrier(order.getShipChannel())
                .trackNumber(trackNumber)
                .events(List.of())
                .note(note)
                .build();
    }

    private static TrackingDtoResponse buildFromCj(LitemallOrderAggregate order, String trackNumber,
                                                   CjTrackingSnapshot cj) {
        List<TrackingDtoResponse.Event> events;
        if (cj.getHops() != null && !cj.getHops().isEmpty()) {
            // CJ's own history, newest first, in the carrier's words.
            events = cj.getHops().stream()
                    .map(h -> new TrackingDtoResponse.Event(h.time(), h.remark(), h.remark(),
                            StringUtils.hasText(h.location()) ? h.location().trim() : null))
                    .toList();
        } else if (StringUtils.hasText(cj.getTrackingStatus())) {
            // No history yet: one entry from the current status, as before.
            events = List.of(new TrackingDtoResponse.Event(cj.getDeliveryTime(), cj.getTrackingStatus(),
                    eventDescription(cj)));
        } else {
            events = List.of();
        }
        return TrackingDtoResponse.builder()
                .shipped(true)
                .status(cj.getTrackingStatus())
                .carrier(StringUtils.hasText(cj.getCarrier()) ? cj.getCarrier() : order.getShipChannel())
                .trackNumber(trackNumber)
                .origin(cj.getOrigin())
                .destination(cj.getDestination())
                .deliveryDay(cj.getDeliveryDay())
                .lastMileCarrier(cj.getLastMileCarrier())
                .lastTrackNumber(cj.getLastTrackNumber())
                .lastMileCarrierPhone(blankToNull(cj.getLastMileCarrierPhone()))
                .lastMileCarrierEmail(blankToNull(cj.getLastMileCarrierEmail()))
                .events(events)
                .build();
    }

    private static TrackingDtoResponse buildFromExpress(LitemallOrderAggregate order, String trackNumber,
                                                        ExpressTrackingSnapshot snapshot) {
        List<TrackingDtoResponse.Event> events = snapshot.getEvents() == null ? List.of()
                : snapshot.getEvents().stream()
                        .map(e -> new TrackingDtoResponse.Event(e.getTime(), e.getStatus(), e.getDescription()))
                        .toList();
        return TrackingDtoResponse.builder()
                .shipped(true)
                .status(snapshot.getStatus())
                .carrier(StringUtils.hasText(snapshot.getCarrier())
                        ? snapshot.getCarrier() : order.getShipChannel())
                .trackNumber(trackNumber)
                .events(events)
                .build();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String eventDescription(CjTrackingSnapshot cj) {
        StringBuilder sb = new StringBuilder(cj.getTrackingStatus());
        if (StringUtils.hasText(cj.getLastMileCarrier())) {
            sb.append(" — last mile: ").append(cj.getLastMileCarrier());
            if (StringUtils.hasText(cj.getLastTrackNumber())) {
                sb.append(" (").append(cj.getLastTrackNumber()).append(')');
            }
        }
        return sb.toString();
    }
}
