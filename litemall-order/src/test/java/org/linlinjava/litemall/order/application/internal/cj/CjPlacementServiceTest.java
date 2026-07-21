package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjRetryableException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderSnapshot;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CjPlacementService}, the Wave-8 owner of decoupled CJ placement: places only
 * paid-and-unplaced CJ orders (stale queue reads skip cleanly), adopts an existing CJ-side
 * order by merchant orderNumber before creating (sweep path), retains the order on retryable
 * failures, parks it with {@code PLACEMENT_REJECTED} + ops signal on terminal rejections
 * (money untouched), and deletes the fresh CJ draft when the order left PAID mid-placement.
 */
@ExtendWith(MockitoExtension.class)
class CjPlacementServiceTest {

    private static final LitemallOrderId ORDER_ID = new LitemallOrderId(71);

    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock
    private CjFulfillmentService cjFulfillmentService;
    @Mock
    private CjLifecycleService cjLifecycleService;
    @Mock
    private CjDropshipOrderFacade cjOrderFacade;
    @Mock
    private CjOpsNotifier opsNotifier;

    @InjectMocks
    private CjPlacementService service;

    private LitemallOrderAggregate paidUnplacedCjOrder() {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER_ID);
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("20260721000071");
        order.setOrderStatus(LitemallOrderStatus.PAID);
        order.setSource(LitemallOrderAggregate.SOURCE_CJ);
        order.setActualPrice(new LitemallMoney(new BigDecimal("25.00")));
        return order;
    }

    @Test
    void paidUnplacedCjOrder_isPlaced_hopRecorded_lifecycleAdvanced() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.place(ORDER_ID, false, true);

        verify(cjFulfillmentService).placeForPaidOrder(eq(order), anyList());
        verify(cjLifecycleService).advance(ORDER_ID);
        ArgumentCaptor<LitemallOrderStatusChange> hop =
                ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertEquals(CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT, hop.getValue().getChangeType());
    }

    @Test
    void alreadyPlacedOrder_isSkipped() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        order.setCjOrderId("cj-1");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.place(ORDER_ID, false, true);

        verify(cjFulfillmentService, never()).placeForPaidOrder(any(), anyList());
        verify(cjLifecycleService, never()).advance(any());
    }

    @Test
    void unpaidOrRefundedOrder_isSkipped_staleQueueReadIsHarmless() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        order.setOrderStatus(LitemallOrderStatus.REFUND_REQUEST);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.place(ORDER_ID, true, false);

        verify(cjOrderFacade, never()).fetchOrderDetail(anyString());
        verify(cjFulfillmentService, never()).placeForPaidOrder(any(), anyList());
    }

    @Test
    void parkedPlacementRejectedOrder_isNotRetried() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        order.setCjOrderStatus(CjPlacementService.STATUS_PLACEMENT_REJECTED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.place(ORDER_ID, false, true);

        verify(cjFulfillmentService, never()).placeForPaidOrder(any(), anyList());
    }

    @Test
    void reconcileFirst_adoptsExistingCjOrderByOrderSn_withoutCreating() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(cjOrderFacade.fetchOrderDetail("20260721000071")).thenReturn(Optional.of(
                new CjOrderSnapshot("cj-adopted-1", "CREATED", null, null, null, "CJPacket Ordinary")));

        service.place(ORDER_ID, true, false);

        verify(orderRepository).recordCjPlacement(ORDER_ID, "cj-adopted-1", null,
                "CJPacket Ordinary", "CREATED");
        verify(cjFulfillmentService, never()).placeForPaidOrder(any(), anyList());
        verify(cjLifecycleService).advance(ORDER_ID);
    }

    @Test
    void retryableFailure_retainsTheOrder_noSentinel_noOpsMail_hopOnlyOnFirstAttempt() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(cjFulfillmentService.placeForPaidOrder(eq(order), anyList()))
                .thenThrow(new LitemallCjRetryableException("CJ ACL disabled"));

        service.place(ORDER_ID, false, true);

        verify(orderRepository, never()).updateCjOrderStatus(any(), anyString());
        verify(opsNotifier, never()).notify(anyString(), anyString());
        // one customer-visible "deferred" hop on the first attempt
        ArgumentCaptor<LitemallOrderStatusChange> hop =
                ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertEquals(CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT, hop.getValue().getChangeType());
        assertTrue(hop.getValue().getChangeMessage().contains("deferred"));
    }

    @Test
    void retryableFailure_onSweepRetry_neverSpamsTheTimeline() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(cjOrderFacade.fetchOrderDetail(anyString())).thenReturn(Optional.empty());
        when(cjFulfillmentService.placeForPaidOrder(eq(order), anyList()))
                .thenThrow(new LitemallCjRetryableException("still down"));

        service.place(ORDER_ID, true, false);

        verify(statusHistoryRepository, never()).record(any());
    }

    @Test
    void terminalRejection_parksTheOrder_recordsFailureHop_notifiesOps_moneyUntouched() {
        LitemallOrderAggregate order = paidUnplacedCjOrder();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(cjFulfillmentService.placeForPaidOrder(eq(order), anyList()))
                .thenThrow(new LitemallCjOrderException("shipping country not supported"));

        service.place(ORDER_ID, false, true);

        verify(orderRepository).updateCjOrderStatus(ORDER_ID,
                CjPlacementService.STATUS_PLACEMENT_REJECTED);
        ArgumentCaptor<LitemallOrderStatusChange> hop =
                ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertEquals(CjPlacementService.CHANGE_TYPE_CJ_PLACEMENT_FAILED, hop.getValue().getChangeType());
        verify(opsNotifier).notify(anyString(), anyString());
        verify(cjLifecycleService, never()).advance(any());
    }

    @Test
    void orderLeavesPaidDuringPlacement_freshCjDraftIsDeleted_notAdvanced() {
        LitemallOrderAggregate paid = paidUnplacedCjOrder();
        LitemallOrderAggregate refunded = paidUnplacedCjOrder();
        refunded.setOrderStatus(LitemallOrderStatus.REFUND_REQUEST);
        refunded.setCjOrderId("cj-fresh-1");
        refunded.setCjOrderStatus("CREATED");
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(paid))   // eligibility check
                .thenReturn(Optional.of(refunded)); // post-placement re-check

        service.place(ORDER_ID, false, true);

        verify(cjFulfillmentService).placeForPaidOrder(eq(paid), anyList());
        verify(cjFulfillmentService).cancelAtCjIfDeletable(eq(refunded), anyString());
        verify(cjLifecycleService, never()).advance(any());
    }
}
