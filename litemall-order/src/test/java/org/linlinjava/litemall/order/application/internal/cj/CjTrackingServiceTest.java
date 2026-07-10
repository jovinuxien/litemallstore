package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjTrackingFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.interfaces.dtos.cj.tracking.TrackingDtoResponse;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * {@link CjTrackingService}: owner scoping mirrors order detail (foreign/absent → null → 404),
 * an unshipped order is a clean NOT_SHIPPED payload (never an error), a shipped CJ order maps
 * the CJ summary into the event-list contract, CJ-no-data degrades to shipped-without-events,
 * and local orders never spend CJ quota.
 */
@ExtendWith(MockitoExtension.class)
class CjTrackingServiceTest {

    private static final LitemallUserId USER = new LitemallUserId(42);
    private static final LitemallOrderId ORDER = new LitemallOrderId(61);

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private CjTrackingFacade trackingFacade;

    @InjectMocks
    private CjTrackingService service;

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
    }

    @Test
    void locallyFulfilledOrder_neverSpendsCjQuota() {
        when(orderRepository.findByIdAndUserId(USER, ORDER))
                .thenReturn(order(LitemallOrderAggregate.SOURCE_LOCAL, "LOCAL-SN-1", "PostNord"));

        TrackingDtoResponse dto = service.getTrackingForUser(USER, ORDER);

        assertTrue(dto.isShipped());
        assertEquals("LOCAL-SN-1", dto.getTrackNumber());
        assertTrue(dto.getEvents().isEmpty());
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
}
