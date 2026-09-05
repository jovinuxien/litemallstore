package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;
import org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrderTrackingService} (ex-CjTrackingService): owner scoping mirrors order detail
 * (foreign/absent → null → 404), an unshipped order is a clean NOT_SHIPPED payload (never
 * an error), a shipped CJ order maps the CJ summary into the event-list contract with NO
 * {@code note}, CJ-no-data degrades to shipped-without-events, and local orders never
 * spend CJ quota. Wave 4: local shipped orders consult the {@link ExpressQueryPort} seam —
 * disabled provider / provider miss note the payload, a hit fills real events.
 */
@ExtendWith(MockitoExtension.class)
class OrderTrackingServiceTest {

    private static final LitemallUserId USER = new LitemallUserId(42);
    private static final LitemallOrderId ORDER = new LitemallOrderId(61);

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private CjTrackingFacade trackingFacade;
    @Mock
    private ExpressQueryPort expressQueryPort;

    @InjectMocks
    private OrderTrackingService service;

    private LitemallOrderAggregate order(String source, String shipSn, String shipChannel) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setUserId(USER);
        order.setSource(source);
        order.setShipSn(shipSn);
        order.setShipChannel(shipChannel);
        return order;
    }

    @Test
    void foreignOrAbsentOrder_returnsNull_forThe404Path() {
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(null);
        assertNull(service.getTrackingForUser(USER, ORDER));
    }

    @Test
    void notShippedYet_isACleanPayload() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_CJ, null, "CJPacket Ordinary"));

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertFalse(dto.isShipped());
        assertEquals("NOT_SHIPPED", dto.getStatus());
        assertTrue(dto.getEvents().isEmpty());
        assertNull(dto.getNote());
        verify(trackingFacade, never()).trackInfo(any());
    }

    @Test
    void shippedCjOrder_mapsTheCjSummaryIntoTheEventContract() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_CJ, "CJPKL123", "CJPacket Ordinary"));
        when(trackingFacade.trackInfo("CJPKL123")).thenReturn(Optional.of(new CjTrackingSnapshot(
                "CJPKL123", "YunExpress", "CN", "SE", "7-15",
                "2026-07-09 14:32:00", "In transit", "PostNord", "SE987")));

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertEquals("In transit", dto.getStatus());
        assertEquals("YunExpress", dto.getCarrier());
        assertEquals("CJPKL123", dto.getTrackNumber());
        assertEquals("CN", dto.getOrigin());
        assertEquals("SE", dto.getDestination());
        assertEquals(1, dto.getEvents().size());
        assertEquals("2026-07-09 14:32:00", dto.getEvents().get(0).getTime());
        assertTrue(dto.getEvents().get(0).getDescription().contains("PostNord"));
        // CJ payloads never carry the Wave-4 local-tracking note.
        assertNull(dto.getNote());
        verify(expressQueryPort, never()).query(any(), any());
    }

    @Test
    void cjHasNoDataYet_degradesToShippedWithoutEvents() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_CJ, "CJPKL123", "CJPacket Ordinary"));
        when(trackingFacade.trackInfo("CJPKL123")).thenReturn(Optional.empty());

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertNull(dto.getStatus());
        assertEquals("CJPacket Ordinary", dto.getCarrier());
        assertEquals("CJPKL123", dto.getTrackNumber());
        assertTrue(dto.getEvents().isEmpty());
        // The CJ branch stays byte-identical to Wave 3: no note, even without CJ data.
        assertNull(dto.getNote());
        verify(expressQueryPort, never()).query(any(), any());
    }

    @Test
    void locallyFulfilledOrder_neverSpendsCjQuota() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_LOCAL, "LOCAL-SN-1", "PostNord"));
        when(expressQueryPort.enabled()).thenReturn(false);

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertEquals("LOCAL-SN-1", dto.getTrackNumber());
        assertTrue(dto.getEvents().isEmpty());
        verify(trackingFacade, never()).trackInfo(any());
    }

    @Test
    void localShipped_providerDisabled_notesTheDisabledProvider() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_LOCAL, "LOCAL-SN-1", "PostNord"));
        when(expressQueryPort.enabled()).thenReturn(false);

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertNull(dto.getStatus());
        assertEquals("PostNord", dto.getCarrier());
        assertEquals("tracking provider disabled", dto.getNote());
        verify(expressQueryPort, never()).query(any(), any());
    }

    @Test
    void localShipped_providerMiss_notesTemporarilyUnavailable() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_LOCAL, "LOCAL-SN-1", "PostNord"));
        when(expressQueryPort.enabled()).thenReturn(true);
        when(expressQueryPort.query("PostNord", "LOCAL-SN-1")).thenReturn(Optional.empty());

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertEquals("LOCAL-SN-1", dto.getTrackNumber());
        assertTrue(dto.getEvents().isEmpty());
        assertEquals("tracking temporarily unavailable", dto.getNote());
    }

    @Test
    void localShipped_providerHit_fillsRealEvents() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_LOCAL, "LOCAL-SN-1", "PostNord"));
        when(expressQueryPort.enabled()).thenReturn(true);
        when(expressQueryPort.query("PostNord", "LOCAL-SN-1")).thenReturn(Optional.of(
                new ExpressTrackingSnapshot("PostNord AB", "LOCAL-SN-1", "In transit", List.of(
                        new ExpressTrackingSnapshot.Event("2026-07-12 09:00:00", null, "Departed sorting hub"),
                        new ExpressTrackingSnapshot.Event("2026-07-11 18:00:00", null, "Collected")))));

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertEquals("In transit", dto.getStatus());
        assertEquals("PostNord AB", dto.getCarrier());
        assertEquals("LOCAL-SN-1", dto.getTrackNumber());
        assertEquals(2, dto.getEvents().size());
        assertEquals("2026-07-12 09:00:00", dto.getEvents().get(0).getTime());
        assertEquals("Departed sorting hub", dto.getEvents().get(0).getDescription());
        assertNull(dto.getNote());
        verify(trackingFacade, never()).trackInfo(any());
    }

    @Test
    void adminRead_isUnscoped() {
        when(orderRepository.findById(ORDER))
                .thenReturn(Optional.of(order(LitemallOrderAggregate.SOURCE_CJ, null, null)));

        TrackingDtoResponse dto = service.getTrackingForAdmin(ORDER);

        assertFalse(dto.isShipped());
        assertEquals("NOT_SHIPPED", dto.getStatus());
    }

    /** F10: shipped at CJ before a number was assigned — say "tracking pending", not "not shipped". */
    @Test
    void shippedWithoutNumberYet_isTrackingPending_notNotShipped() {
        LitemallOrderAggregate order = order(LitemallOrderAggregate.SOURCE_CJ, null, "CJPacket Ordinary");
        order.setOrderStatus(org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus.SHIPPED);
        when(orderRepository.findByIdAndUserId(USER, ORDER)).thenReturn(order);

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertEquals(OrderTrackingService.STATUS_TRACKING_PENDING, dto.getStatus());
        assertEquals("CJPacket Ordinary", dto.getCarrier());
        assertTrue(dto.getEvents().isEmpty());
    }
}
