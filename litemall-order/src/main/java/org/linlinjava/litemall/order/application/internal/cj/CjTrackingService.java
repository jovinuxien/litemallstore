package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Shipment tracking read (Wave 3, Task E). Owner-scoped for customers (an absent or
 * foreign order returns {@code null} → the controller's 404, same convention as order
 * detail/timeline); unscoped for the admin surface. An order without a tracking number
 * yet is a clean {@code NOT_SHIPPED} payload, never an error.
 *
 * <p>Live tracking comes from CJ ({@link CjTrackingFacade}, 1h cache) for CJ-fulfilled
 * orders; locally-fulfilled orders return carrier + tracking number without live events
 * (no live-tracking source for arbitrary local carriers — noted in the handoff spec).
 */
@Service
public class CjTrackingService {

    private final LitemallOrderRepository orderRepository;
    private final CjTrackingFacade trackingFacade;

    public CjTrackingService(LitemallOrderRepository orderRepository, CjTrackingFacade trackingFacade) {
        this.orderRepository = orderRepository;
        this.trackingFacade = trackingFacade;
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
        CjTrackingSnapshot cj = order.isCjFulfilled()
                ? trackingFacade.trackInfo(trackNumber).orElse(null)
                : null;
        if (cj == null) {
            // Local carrier, or CJ has no data (yet) / is unreachable: still a clean payload.
            return TrackingDtoResponse.builder()
                    .shipped(true)
                    .status(null)
                    .carrier(order.getShipChannel())
                    .trackNumber(trackNumber)
                    .events(List.of())
                    .build();
        }
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
