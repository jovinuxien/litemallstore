package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallPromotionFacade;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 21: a cancelled order that occupied a group-buy slot frees it at promotion
 * ({@code POST /pink/{pinkId}/release}) — both the customer-cancel and the
 * unpaid-timeout auto-cancel paths; an order without a pinkId never calls release.
 * (Fail-softness of the call itself lives in the facade and its tests.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallOrderCancelPinkReleaseTest {

    private static final int PINK = 400;

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponRepository grouponRepository;
    @Mock
    private LitemallCartRepository cartRepository;
    @Mock
    private LitemallAddressRepository addressRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private LitemallCouponServiceLayer couponService;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;
    @Mock
    private LitemallGoodsFacade goodsFacade;
    @Mock
    private LitemallPromotionFacade promotionFacade;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService cjFulfillmentService;

    @InjectMocks
    private LitemallOrderServiceImpl service;

    private LitemallOrderAggregate createdOrder(Integer pinkId) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(77));
        order.setUserId(new LitemallUserId(99));
        order.setOrderSn("SN-1");
        order.setOrderStatus(LitemallOrderStatus.CREATED);
        order.setAfterSaleStatus(LitemallAfterSaleStatus.STATUS_INIT);
        order.setCouponPrice(new LitemallMoney(BigDecimal.ZERO));
        order.setPinkId(pinkId);
        return order;
    }

    private void wire(LitemallOrderAggregate order) {
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "promotionFacade", promotionFacade);
        ReflectionTestUtils.setField(service, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(service, "cjFulfillmentService", cjFulfillmentService);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderRepository.markCanceledIfCreated(any())).thenReturn(1);
        when(orderRepository.markSystemCanceledIfCreated(any())).thenReturn(1);
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of());
    }

    @Test
    void customerCancel_releasesThePinkSlot() {
        LitemallOrderAggregate order = createdOrder(PINK);
        wire(order);

        service.cancelOrder(new LitemallOrderId(77), "changed my mind");

        // No active transaction in the unit test → the hook calls through inline.
        // (LitemallUserId has no equals — match by id.)
        verify(promotionFacade).releasePinkSlot(
                org.mockito.ArgumentMatchers.argThat(u -> u != null && u.getId() == 99),
                eq(PINK), eq(new LitemallOrderId(77)));
    }

    @Test
    void unpaidTimeoutAutoCancel_releasesThePinkSlot() {
        LitemallOrderAggregate order = createdOrder(PINK);
        wire(order);

        service.autoCancelOrder(new LitemallOrderId(77), "unpaid timeout");

        verify(promotionFacade).releasePinkSlot(
                org.mockito.ArgumentMatchers.argThat(u -> u != null && u.getId() == 99),
                eq(PINK), eq(new LitemallOrderId(77)));
    }

    @Test
    void cancelWithoutPinkId_neverCallsRelease() {
        LitemallOrderAggregate order = createdOrder(null);
        wire(order);

        service.cancelOrder(new LitemallOrderId(77), "changed my mind");

        verify(promotionFacade, never()).releasePinkSlot(any(), any(), any());
    }
}
