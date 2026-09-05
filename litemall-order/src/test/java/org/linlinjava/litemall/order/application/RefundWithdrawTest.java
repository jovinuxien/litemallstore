package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderStatusQuery;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lifecycle package C, decision D4: a customer can withdraw a refund request that no admin
 * has decided yet, and the order goes back to exactly where it was. Plus the F18 helper
 * clean-up: the status helpers no longer contradict the dispatcher.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundWithdrawTest {

    private static final LitemallOrderId ORDER = new LitemallOrderId(21);
    private static final LitemallUserId USER = new LitemallUserId(42);

    @Mock private LitemallOrderServiceImpl orderServiceImpl;
    @Mock private LitemallOrderRepository orderRepository;
    @Mock private LitemallGrouponServiceLayer grouponServiceLayer;

    private LitemallOrderOrchestratorService orchestrator;

    @BeforeEach
    void wire() {
        orchestrator = new LitemallOrderOrchestratorService(orderServiceImpl, orderRepository, grouponServiceLayer);
    }

    private static LitemallOrderAggregate order(LitemallOrderStatus status, LocalDateTime shipTime, LocalDateTime confirmTime) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setUserId(USER);
        order.setOrderStatus(status);
        order.setShipTime(shipTime);
        order.setConfirmTime(confirmTime);
        return order;
    }

    // ------------------------------------------------------------------
    // Aggregate
    // ------------------------------------------------------------------

    @Test
    void aggregate_withdrawFromAPaidOrder_goesBackToPaid() {
        LitemallOrderAggregate order = order(LitemallOrderStatus.REFUND_REQUEST, null, null);

        LitemallOrderStatus backTo = order.withdrawRefundRequest();

        assertThat(backTo).isEqualTo(LitemallOrderStatus.PAID);
        assertThat(order.getOrderStatus()).isEqualTo(LitemallOrderStatus.PAID);
        assertThat(order.getStatusChanges().get(0).getChangeType()).isEqualTo("refund_withdrawn");
        assertThat(order.getStatusChanges().get(0).getOperator()).isEqualTo("user");
    }

    @Test
    void aggregate_withdrawFromAShippedOrder_goesBackToShipped() {
        LitemallOrderAggregate order = order(LitemallOrderStatus.REFUND_REQUEST, LocalDateTime.now().minusDays(2), null);

        assertThat(order.withdrawRefundRequest()).isEqualTo(LitemallOrderStatus.SHIPPED);
    }

    @Test
    void aggregate_deliveredOrderRequest_isNotWithdrawable() {
        LitemallOrderAggregate order = order(LitemallOrderStatus.REFUND_REQUEST,
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(1));

        assertThatThrownBy(order::withdrawRefundRequest).isInstanceOf(IllegalStateException.class);
        assertThat(order.getOrderStatus()).isEqualTo(LitemallOrderStatus.REFUND_REQUEST);
    }

    @Test
    void aggregate_refundedOrder_isNotWithdrawable() {
        assertThatThrownBy(() -> order(LitemallOrderStatus.REFUNDED, null, null).withdrawRefundRequest())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitionGraph_allowsWithdrawal_toPaidOrShipped_only() {
        assertThat(LitemallOrderStatus.REFUND_REQUEST.canTransitionTo(LitemallOrderStatus.PAID)).isTrue();
        assertThat(LitemallOrderStatus.REFUND_REQUEST.canTransitionTo(LitemallOrderStatus.SHIPPED)).isTrue();
        assertThat(LitemallOrderStatus.REFUND_REQUEST.canTransitionTo(LitemallOrderStatus.REFUNDED)).isTrue();
        assertThat(LitemallOrderStatus.REFUND_REQUEST.canTransitionTo(LitemallOrderStatus.DELIVERED)).isFalse();
        assertThat(LitemallOrderStatus.REFUND_REQUEST.canTransitionTo(LitemallOrderStatus.CANCELED)).isFalse();
    }

    // ------------------------------------------------------------------
    // Handle option: 202 is no longer a dead end
    // ------------------------------------------------------------------

    @Test
    void refundRequestStatus_offersWithdraw_andNothingElse() {
        LitemallOrderHandleOption option = LitemallOrderHandleOption.forStatus(LitemallOrderStatus.REFUND_REQUEST);

        assertThat(option.isWithdrawRefund()).isTrue();
        assertThat(option.isRefund()).isFalse();
        assertThat(option.isCancel()).isFalse();
        assertThat(option.isDelete()).isFalse();
        assertThat(LitemallOrderHandleOption.forStatus(LitemallOrderStatus.PAID).isWithdrawRefund()).isFalse();
    }

    // ------------------------------------------------------------------
    // Orchestrator
    // ------------------------------------------------------------------

    @Test
    void orchestrator_withdraw_isOwnerScoped_andReturnsTheRestoredOptions() {
        LitemallOrderAggregate order = order(LitemallOrderStatus.REFUND_REQUEST, null, null);
        when(orderRepository.findById(ORDER)).thenReturn(java.util.Optional.of(order));
        when(orderServiceImpl.withdrawRefundRequest(ORDER)).thenReturn(LitemallOrderStatus.PAID);

        LitemallOrderOperationResult result = orchestrator.withdrawRefundRequest(ORDER, USER);

        assertThat(result.isSuccess()).isTrue();
        verify(orderServiceImpl).withdrawRefundRequest(ORDER);
    }

    @Test
    void orchestrator_withdrawOnANonRefundRequestOrder_isInvalidState() {
        when(orderRepository.findById(ORDER)).thenReturn(java.util.Optional.of(order(LitemallOrderStatus.PAID, null, null)));

        LitemallOrderOperationResult result = orchestrator.withdrawRefundRequest(ORDER, USER);

        assertThat(result.isSuccess()).isFalse();
        verify(orderServiceImpl, never()).withdrawRefundRequest(any());
    }

    @Test
    void orchestrator_notWithdrawable_isATypedFailure_notA500() {
        when(orderRepository.findById(ORDER))
                .thenReturn(java.util.Optional.of(order(LitemallOrderStatus.REFUND_REQUEST, null, null)));
        when(orderServiceImpl.withdrawRefundRequest(ORDER))
                .thenThrow(new IllegalStateException("A refund request on a delivered order cannot be withdrawn"));

        LitemallOrderOperationResult result = orchestrator.withdrawRefundRequest(ORDER, USER);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("delivered");
    }

    @Test
    void orchestrator_foreignOrder_isNotFound() {
        when(orderRepository.findById(ORDER)).thenReturn(java.util.Optional.empty());

        assertThat(orchestrator.withdrawRefundRequest(ORDER, USER).isSuccess()).isFalse();
    }

    // ------------------------------------------------------------------
    // F18: helpers agree with the dispatcher
    // ------------------------------------------------------------------

    @Test
    void canBeCanceled_isCreatedOnly_likeTheDispatcher() {
        assertThat(LitemallOrderStatusQuery.canBeCanceled(order(LitemallOrderStatus.CREATED, null, null))).isTrue();
        assertThat(LitemallOrderStatusQuery.canBeCanceled(order(LitemallOrderStatus.PAID, null, null))).isFalse();
    }

    @Test
    void isFinalStatus_coversSystemCancelledAndRefunded_notDelivered() {
        assertThat(LitemallOrderStatusQuery.isFinalStatus(order(LitemallOrderStatus.SYSTEM_CANCELED, null, null))).isTrue();
        assertThat(LitemallOrderStatusQuery.isFinalStatus(order(LitemallOrderStatus.REFUNDED, null, null))).isTrue();
        // A delivered order can still open a refund; it is not the end of the line.
        assertThat(LitemallOrderStatusQuery.isFinalStatus(order(LitemallOrderStatus.DELIVERED, null, null))).isFalse();
    }
}
