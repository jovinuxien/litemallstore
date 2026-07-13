package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
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
 * "tracking temporarily unavailable"). CJ payloads never carry a {@code note}.
 */
@Service
public class OrderTrackingService {

    static final String NOTE_PROVIDER_DISABLED = "tracking provider disabled";
    static final String NOTE_TEMPORARILY_UNAVAILABLE = "tracking temporarily unavailable";

    private final LitemallOrderRepository orderRepository;
    private final CjTrackingFacade trackingFacade;
    private final ExpressQueryPort expressQueryPort;

    public OrderTrackingService(LitemallOrderRepository orderRepository, CjTrackingFacade trackingFacade,
                                ExpressQueryPort expressQueryPort) {
        this.orderRepository = orderRepository;
        this.trackingFacade = trackingFacade;
        this.expressQueryPort = expressQueryPort;
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

    private TrackingDtoResponse build(LitemallOrderAggregate order) {
        String trackNumber = order.getShipSn();
        if (!StringUtils.hasText(trackNumber)) {
            return TrackingDtoResponse.builder()
                    .shipped(false)
                    .status("NOT_SHIPPED")
                    .carrier(order.getShipChannel())
                    .events(List.of())
                    .build();
        }
        if (order.isCjFulfilled()) {
            // CJ branch — byte-identical to Wave 3, including "CJ has no data (yet) /
            // unreachable" degrading to shipped-without-events with NO note.
            CjTrackingSnapshot cj = trackingFacade.trackInfo(trackNumber).orElse(null);
            return cj == null
                    ? shippedWithoutEvents(order, trackNumber, null)
                    : buildFromCj(order, trackNumber, cj);
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
        List<TrackingDtoResponse.Event> events = StringUtils.hasText(cj.getTrackingStatus())
                ? List.of(new TrackingDtoResponse.Event(cj.getDeliveryTime(), cj.getTrackingStatus(),
                        eventDescription(cj)))
                : List.of();
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
