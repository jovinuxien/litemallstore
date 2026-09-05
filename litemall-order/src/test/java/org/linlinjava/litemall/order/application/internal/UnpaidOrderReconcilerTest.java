package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * F1 of plan-order-lifecycle-e2e.md: the unpaid-order sweep must never cancel an order
 * whose PaymentIntent has captured or is capturing, and must cancel the intent at the
 * PSP BEFORE cancelling the order so a late confirmation cannot take the money.
 */
class UnpaidOrderReconcilerTest {

    private static final LitemallOrderId ORDER = new LitemallOrderId(77);

    private final LitemallOrderRepository orderRepository = mock(LitemallOrderRepository.class);
    private final LitemallOrderServiceImpl orderService = mock(LitemallOrderServiceImpl.class);
    private final LitemallOrderOrchestratorService orchestrator = mock(LitemallOrderOrchestratorService.class);
    private final LitemallOrderStatusHistoryRepository history = mock(LitemallOrderStatusHistoryRepository.class);
    private final PaymentGatewayPort gateway = mock(PaymentGatewayPort.class);

    private UnpaidOrderReconciler reconciler;

    @BeforeEach
    void wire() {
        reconciler = new UnpaidOrderReconciler(orderRepository, orderService, orchestrator, history, gateway, 60, 5);
    }

    private LitemallOrderAggregate created(String intentId) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setOrderStatus(LitemallOrderStatus.CREATED);
        order.setPaymentIntentId(intentId);
        return order;
    }

    private static PaymentIntentState state(PaymentIntentState.Status status, Integer orderId) {
        return PaymentIntentState.of(status, orderId, status == PaymentIntentState.Status.SUCCEEDED ? 858L : 0L,
                "eur", status.name().toLowerCase());
    }

    @Test
    void missingOrder_throwsNoSuchElement_soTheSweepRetiresTheTask() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reconciler.resolve(ORDER)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void orderNoLongerCreated_isRetiredWithoutTouchingAnything() {
        LitemallOrderAggregate paid = created("pi_1");
        paid.setOrderStatus(LitemallOrderStatus.PAID);
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(paid));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.getKind()).isEqualTo(UnpaidOrderReconciler.Outcome.Kind.RETIRED);
        verifyNoMoreInteractions(gateway, orderService, orchestrator);
    }

    /** No intent was ever minted (wallet checkout, or the customer never reached payment): today's behaviour. */
    @Test
    void noIntent_cancelsTheOrderWithoutAskingThePsp() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created(null)));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.getKind()).isEqualTo(UnpaidOrderReconciler.Outcome.Kind.CANCELLED);
        verify(orderService).autoCancelOrder(eq(ORDER), anyString());
        verifyNoMoreInteractions(gateway);
    }

    /** The headline case: the customer paid late (or asynchronously). The order is PAID, not cancelled. */
    @Test
    void succeededIntent_settlesTheOrderInsteadOfCancelling() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.SUCCEEDED, 77));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.getKind()).isEqualTo(UnpaidOrderReconciler.Outcome.Kind.PAID);
        verify(orchestrator).settleVerifiedPspPayment(ORDER, "pi_1");
        verify(orderService, never()).autoCancelOrder(any(), anyString());
        verify(gateway, never()).cancelIntent(anyString());
        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(history).record(hop.capture());
        assertThat(hop.getValue().getChangeType()).isEqualTo(UnpaidOrderReconciler.CHANGE_TYPE_PAYMENT_RECONCILED);
    }

    /** A succeeded intent tagged for ANOTHER order is not evidence for this one: defer, never act. */
    @Test
    void succeededIntentForAnotherOrder_defersAndDoesNothing() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.SUCCEEDED, 99));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.isDeferred()).isTrue();
        verifyNoMoreInteractions(orchestrator, orderService);
    }

    /** SEPA and friends: money is in flight for days. The order waits; the task moves out by the processing delay. */
    @Test
    void processingIntent_defersByTheProcessingWindow() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.PROCESSING, 77));
        LocalDateTime before = LocalDateTime.now();

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.isDeferred()).isTrue();
        assertThat(outcome.getDeferUntil()).isAfterOrEqualTo(before.plusMinutes(59));
        assertThat(outcome.getDeferUntil()).isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(61));
        verify(orderService, never()).autoCancelOrder(any(), anyString());
        verify(gateway, never()).cancelIntent(anyString());
    }

    /** Stripe unreachable: we do not know, so we do not cancel. Short deferral. */
    @Test
    void unavailablePsp_defersByTheShortWindow_neverCancelsBlind() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(PaymentIntentState.unavailable("connection reset"));
        LocalDateTime before = LocalDateTime.now();

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.isDeferred()).isTrue();
        assertThat(outcome.getDeferUntil()).isAfterOrEqualTo(before.plusMinutes(4));
        assertThat(outcome.getDeferUntil()).isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(6));
        verify(orderService, never()).autoCancelOrder(any(), anyString());
    }

    /** The common abandon case: intent never confirmed. Cancel it at Stripe FIRST, then the order. */
    @Test
    void pendingIntent_isCancelledAtThePspBeforeTheOrderIsCancelled() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.PENDING, 77));
        when(gateway.cancelIntent("pi_1")).thenReturn(state(PaymentIntentState.Status.CANCELED, 77));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.getKind()).isEqualTo(UnpaidOrderReconciler.Outcome.Kind.CANCELLED);
        var order = inOrder(gateway, orderService);
        order.verify(gateway).cancelIntent("pi_1");
        order.verify(orderService).autoCancelOrder(eq(ORDER), anyString());
    }

    /** The race the plan names: it succeeded between inspect and cancel. Stripe's refusal wins — settle. */
    @Test
    void pendingIntentThatSucceedsUnderUs_isSettledNotCancelled() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.PENDING, 77));
        when(gateway.cancelIntent("pi_1")).thenReturn(state(PaymentIntentState.Status.SUCCEEDED, 77));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.getKind()).isEqualTo(UnpaidOrderReconciler.Outcome.Kind.PAID);
        verify(orchestrator).settleVerifiedPspPayment(ORDER, "pi_1");
        verify(orderService, never()).autoCancelOrder(any(), anyString());
    }

    /** Cancel attempt answered "could not ask": the order stays, the task defers. */
    @Test
    void pendingIntentWhoseCancelFailsTransiently_defers() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.PENDING, 77));
        when(gateway.cancelIntent("pi_1")).thenReturn(PaymentIntentState.unavailable("timeout"));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.isDeferred()).isTrue();
        verify(orderService, never()).autoCancelOrder(any(), anyString());
    }

    /** Already cancelled at Stripe (e.g. by a previous sweep whose order-cancel then failed): nothing to cancel at the PSP. */
    @Test
    void alreadyCancelledIntent_cancelsTheOrderWithoutAnotherPspCall() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created("pi_1")));
        when(gateway.inspect("pi_1")).thenReturn(state(PaymentIntentState.Status.CANCELED, 77));

        UnpaidOrderReconciler.Outcome outcome = reconciler.resolve(ORDER);

        assertThat(outcome.getKind()).isEqualTo(UnpaidOrderReconciler.Outcome.Kind.CANCELLED);
        verify(gateway, never()).cancelIntent(anyString());
        verify(orderService).autoCancelOrder(eq(ORDER), anyString());
    }
}
