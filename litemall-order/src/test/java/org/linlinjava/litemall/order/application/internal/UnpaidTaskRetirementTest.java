package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression cover for the unpaid-order sweep loop fixed 2026-08-27.
 *
 * <p>A customer-cancelled order kept its {@code litemall_unpaid_order_task} row
 * (nothing on the cancel path retired it), and {@code autoCancelOrder} validated
 * the transition on the aggregate BEFORE its own compare-and-set — so the sweep
 * met a CANCELED order, threw {@code IllegalStateException}, kept the row "for
 * retry", and repeated every 60s. One production row survived 35 days that way.
 *
 * <p>These tests pin the half of the fix that lives in the order service: the
 * sweep's entry point must SKIP an order that is no longer CREATED instead of
 * throwing. The task-retirement half is in {@link UnpaidOrderTaskSchedulerTest},
 * the cancel-path half in {@code UnpaidTaskCancelledOnOrderCancelTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnpaidTaskRetirementTest {

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
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

    private LitemallOrderAggregate order(LitemallOrderStatus status) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(8));
        order.setUserId(new LitemallUserId(99));
        order.setOrderSn("20260723691950");
        order.setOrderStatus(status);
        order.setAfterSaleStatus(LitemallAfterSaleStatus.STATUS_INIT);
        order.setCouponPrice(new LitemallMoney(BigDecimal.ZERO));
        return order;
    }

    private void wire(LitemallOrderAggregate order, int casResult) {
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "promotionFacade", promotionFacade);
        ReflectionTestUtils.setField(service, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(service, "cjFulfillmentService", cjFulfillmentService);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderRepository.markSystemCanceledIfCreated(any())).thenReturn(casResult);
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of());
    }

    /** THE BUG: an already-cancelled order threw here, and the sweep looped on it forever. */
    @Test
    void autoCancel_ofAnAlreadyCancelledOrder_isSkippedNotThrown() {
        LitemallOrderAggregate cancelled = order(LitemallOrderStatus.CANCELED);
        wire(cancelled, 0); // CAS matches nothing: the row is not CREATED

        assertThatCode(() -> service.autoCancelOrder(new LitemallOrderId(8), "unpaid timeout"))
                .doesNotThrowAnyException();

        // Skipped means untouched: no status rewrite, no history, no CJ call.
        assertThat(cancelled.getOrderStatus()).isEqualTo(LitemallOrderStatus.CANCELED);
        verify(statusHistoryRepository, never()).record(any());
        verify(cjFulfillmentService, never()).cancelAtCjIfDeletable(any(), any());
    }

    /** Same skip for an order the customer paid in the race window. */
    @Test
    void autoCancel_ofAPaidOrder_isSkippedNotThrown() {
        LitemallOrderAggregate paid = order(LitemallOrderStatus.PAID);
        wire(paid, 0);

        assertThatCode(() -> service.autoCancelOrder(new LitemallOrderId(8), "unpaid timeout"))
                .doesNotThrowAnyException();

        assertThat(paid.getOrderStatus()).isEqualTo(LitemallOrderStatus.PAID);
        verify(statusHistoryRepository, never()).record(any());
    }

    /** The happy path must survive the reorder: a CREATED order still auto-cancels. */
    @Test
    void autoCancel_ofACreatedOrder_stillCancelsAndRecordsHistory() {
        LitemallOrderAggregate created = order(LitemallOrderStatus.CREATED);
        wire(created, 1); // CAS wins

        service.autoCancelOrder(new LitemallOrderId(8), "unpaid timeout");

        assertThat(created.getOrderStatus()).isEqualTo(LitemallOrderStatus.SYSTEM_CANCELED);
        assertThat(created.getEndTime()).isNotNull();
        verify(statusHistoryRepository).record(any());
    }
}
