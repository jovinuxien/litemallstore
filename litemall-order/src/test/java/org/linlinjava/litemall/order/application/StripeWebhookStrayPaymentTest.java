package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.db.dao.StripeEventMapper;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.UnpaidOrderTaskScheduler;
import org.linlinjava.litemall.order.application.internal.cj.CjOpsNotifier;
import org.linlinjava.litemall.order.application.internal.cj.CjPlacementService;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentTemporarilyUnavailableException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.payment.LitemallStrayPaymentRefundedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentVerification;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentWebhookEvent;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * F1/F2 of plan-order-lifecycle-e2e.md on {@link LitemallOrderOrchestratorService#handleStripeWebhook}:
 * a captured charge on an order that can no longer accept it is REFUNDED (never kept and
 * logged), and a PSP outage during verification is surfaced so Stripe redelivers instead
 * of the event id being burned.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripeWebhookStrayPaymentTest {

    private static final LitemallOrderId ORDER = new LitemallOrderId(31);

    @Mock private LitemallOrderServiceImpl orderServiceImpl;
    @Mock private LitemallOrderRepository orderRepository;
    @Mock private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock private PaymentGatewayPort gateway;
    @Mock private StripeEventMapper stripeEventMapper;
    @Mock private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock private CjOpsNotifier opsNotifier;
    @Mock private LitemallDomainEventPublisher domainEventPublisher;
    @Mock private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;
    @Mock private CjPlacementService cjPlacementService;

    private LitemallOrderOrchestratorService orchestrator;

    @BeforeEach
    void wire() {
        orchestrator = new LitemallOrderOrchestratorService(orderServiceImpl, orderRepository, grouponServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "paymentGatewayPort", gateway);
        ReflectionTestUtils.setField(orchestrator, "stripeEventMapper", stripeEventMapper);
        ReflectionTestUtils.setField(orchestrator, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(orchestrator, "opsNotifier", opsNotifier);
        ReflectionTestUtils.setField(orchestrator, "domainEventPublisher", domainEventPublisher);
        ReflectionTestUtils.setField(orchestrator, "unpaidOrderTaskScheduler", unpaidOrderTaskScheduler);
        ReflectionTestUtils.setField(orchestrator, "cjPlacementService", cjPlacementService);
        when(stripeEventMapper.claim(anyString(), anyString(), any())).thenReturn(1);
        when(gateway.parseWebhook(anyString(), anyString())).thenAnswer(inv ->
                new PaymentWebhookEvent("evt_1", "payment_intent.succeeded", "pi_late", 31, 858L, "eur"));
    }

    private LitemallOrderAggregate order(LitemallOrderStatus status, String recordedIntent) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("20260905000031");
        order.setOrderStatus(status);
        order.setActualPrice(new LitemallMoney(new BigDecimal("8.58")));
        order.setPaymentIntentId(recordedIntent);
        order.setSource(LitemallOrderAggregate.SOURCE_LOCAL);
        return order;
    }

    private static PaymentIntentState succeededFor(int orderId) {
        return PaymentIntentState.of(PaymentIntentState.Status.SUCCEEDED, orderId, 858L, "eur", "succeeded");
    }

    // ------------------------------------------------------------------
    // The normal duplicate: already paid by THIS intent
    // ------------------------------------------------------------------

    @Test
    void alreadyPaidByTheSameIntent_isANoOp() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.PAID, "pi_late")));

        orchestrator.handleStripeWebhook("{}", "sig");

        verify(gateway, never()).inspect(anyString());
        verify(gateway, never()).refund(anyString(), any(), anyInt(), anyString());
        verifyNoMoreInteractions(statusHistoryRepository, opsNotifier);
    }

    // ------------------------------------------------------------------
    // The money bug: charge after SYSTEM_CANCELED
    // ------------------------------------------------------------------

    @Test
    void chargeOnAnAutoCancelledOrder_isRefundedUnderItsOwnIdempotencyScope() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_late")));
        when(gateway.inspect("pi_late")).thenReturn(succeededFor(31));
        when(gateway.refund(eq("pi_late"), any(), eq(31), eq("pi_late"))).thenReturn(RefundOutcome.succeeded("re_9"));

        orchestrator.handleStripeWebhook("{}", "sig");

        // The amount refunded is what Stripe captured, not what the order row says.
        ArgumentCaptor<LitemallMoney> amount = ArgumentCaptor.forClass(LitemallMoney.class);
        verify(gateway).refund(eq("pi_late"), amount.capture(), eq(31), eq("pi_late"));
        assertThat(amount.getValue().getAmount()).isEqualByComparingTo("8.58");
        // Never the plain per-order key — that belongs to the order's real refund path.
        verify(gateway, never()).refund(anyString(), any(), anyInt());

        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertThat(hop.getValue().getChangeType())
                .isEqualTo(LitemallOrderOrchestratorService.CHANGE_TYPE_STRAY_PAYMENT_REFUNDED);
        assertThat(hop.getValue().getChangeMessage()).contains("pi_late").contains("re_9").contains("SYSTEM_CANCELED");
        assertThat(hop.getValue().getToStatus()).isEqualTo(LitemallOrderStatus.SYSTEM_CANCELED); // same-status hop

        ArgumentCaptor<org.linlinjava.litemall.core.events.LitemallDomainEvent> event =
                ArgumentCaptor.forClass(org.linlinjava.litemall.core.events.LitemallDomainEvent.class);
        verify(domainEventPublisher).publish(event.capture());
        assertThat(event.getValue()).isInstanceOf(LitemallStrayPaymentRefundedEvent.class);
        LitemallStrayPaymentRefundedEvent refunded = (LitemallStrayPaymentRefundedEvent) event.getValue();
        assertThat(refunded.getAmount()).isEqualByComparingTo("8.58");
        assertThat(refunded.getRefundId()).isEqualTo("re_9");

        // The order itself is untouched: no paid flip, no ops alert.
        verify(orderServiceImpl, never()).markOrderPaid(any(), anyString(), anyString());
        verifyNoMoreInteractions(opsNotifier);
    }

    /** A second intent on an order already paid by the first is the same shape: refund the duplicate. */
    @Test
    void secondChargeOnAPaidOrder_isRefunded() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.PAID, "pi_first")));
        when(gateway.inspect("pi_late")).thenReturn(succeededFor(31));
        when(gateway.refund(eq("pi_late"), any(), eq(31), eq("pi_late"))).thenReturn(RefundOutcome.succeeded("re_dup"));

        orchestrator.handleStripeWebhook("{}", "sig");

        verify(gateway).refund(eq("pi_late"), any(), eq(31), eq("pi_late"));
        verify(domainEventPublisher).publish(any(LitemallStrayPaymentRefundedEvent.class));
    }

    @Test
    void refundRefusedByStripe_writesTheHopAndAlertsOps_withoutThrowing() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_late")));
        when(gateway.inspect("pi_late")).thenReturn(succeededFor(31));
        when(gateway.refund(eq("pi_late"), any(), eq(31), eq("pi_late"))).thenReturn(RefundOutcome.failed("charge disputed"));

        orchestrator.handleStripeWebhook("{}", "sig");

        ArgumentCaptor<LitemallOrderStatusChange> hop = ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertThat(hop.getValue().getChangeType())
                .isEqualTo(LitemallOrderOrchestratorService.CHANGE_TYPE_STRAY_PAYMENT_UNREFUNDED);
        assertThat(hop.getValue().getChangeMessage()).contains("charge disputed");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(opsNotifier).notify(anyString(), body.capture());
        assertThat(body.getValue()).contains("pi_late").contains("manually");
        // No customer mail claims a refund that did not happen.
        verify(domainEventPublisher, never()).publish(any(LitemallStrayPaymentRefundedEvent.class));
    }

    /** The intent on a cancelled order that never captured (Stripe cancelled it): nothing to return. */
    @Test
    void uncapturedIntentOnACancelledOrder_returnsNothingAndWritesNothing() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_late")));
        when(gateway.inspect("pi_late")).thenReturn(
                PaymentIntentState.of(PaymentIntentState.Status.CANCELED, 31, 0L, "eur", "canceled"));

        orchestrator.handleStripeWebhook("{}", "sig");

        verify(gateway, never()).refund(anyString(), any(), anyInt(), anyString());
        verifyNoMoreInteractions(statusHistoryRepository, opsNotifier);
    }

    @Test
    void strayInspectionWhileStripeIsDown_throwsSoStripeRedelivers() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_late")));
        when(gateway.inspect("pi_late")).thenReturn(PaymentIntentState.unavailable("503 from Stripe"));

        assertThatThrownBy(() -> orchestrator.handleStripeWebhook("{}", "sig"))
                .isInstanceOf(LitemallPaymentTemporarilyUnavailableException.class);
        verify(gateway, never()).refund(anyString(), any(), anyInt(), anyString());
    }

    // ------------------------------------------------------------------
    // F2: transient verification failure must not burn the event id
    // ------------------------------------------------------------------

    @Test
    void verificationUnavailable_throwsTemporarilyUnavailable_insteadOfReturningNormally() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.CREATED, "pi_late")));
        when(gateway.verify(eq("pi_late"), eq(31), any())).thenReturn(PaymentVerification.unavailable("connection reset"));

        assertThatThrownBy(() -> orchestrator.handleStripeWebhook("{}", "sig"))
                .isInstanceOf(LitemallPaymentTemporarilyUnavailableException.class)
                .hasMessageContaining("evt_1");
        verify(orderServiceImpl, never()).markOrderPaid(any(), anyString(), anyString());
    }

    @Test
    void verificationRejected_isFinal_noThrowNoPaidFlip() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.CREATED, "pi_late")));
        when(gateway.verify(eq("pi_late"), eq(31), any())).thenReturn(PaymentVerification.rejected("amount mismatch"));

        orchestrator.handleStripeWebhook("{}", "sig");

        verify(orderServiceImpl, never()).markOrderPaid(any(), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // Happy path + the lost race
    // ------------------------------------------------------------------

    @Test
    void verifiedPayment_settlesRecordsTheTenderAndRetiresTheUnpaidTask() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.CREATED, "pi_late")));
        when(gateway.verify(eq("pi_late"), eq(31), any())).thenReturn(PaymentVerification.accepted());

        orchestrator.handleStripeWebhook("{}", "sig");

        verify(orderServiceImpl).markOrderPaid(ORDER, "CREDIT_CARD:pi_late", "pi_late");
        verify(unpaidOrderTaskScheduler).cancel(ORDER);
        verify(gateway, never()).refund(anyString(), any(), anyInt(), anyString());
    }

    /**
     * The sweep cancelled the order between the payable check and the CAS. Before: the
     * IllegalStateException propagated into the controller's deliberate 200 and the money
     * stayed captured. Now: the charge is treated as stray and refunded.
     */
    @Test
    void lostRaceAgainstTheSweep_refundsTheCharge() {
        LitemallOrderAggregate created = order(LitemallOrderStatus.CREATED, "pi_late");
        LitemallOrderAggregate cancelled = order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_late");
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(created), Optional.of(cancelled));
        when(gateway.verify(eq("pi_late"), eq(31), any())).thenReturn(PaymentVerification.accepted());
        doThrow(new IllegalStateException("Order 31 is no longer in CREATED state"))
                .when(orderServiceImpl).markOrderPaid(any(), anyString(), anyString());
        when(gateway.inspect("pi_late")).thenReturn(succeededFor(31));
        when(gateway.refund(eq("pi_late"), any(), eq(31), eq("pi_late"))).thenReturn(RefundOutcome.succeeded("re_race"));

        orchestrator.handleStripeWebhook("{}", "sig");

        verify(gateway).refund(eq("pi_late"), any(), eq(31), eq("pi_late"));
        verify(domainEventPublisher).publish(any(LitemallStrayPaymentRefundedEvent.class));
        verify(unpaidOrderTaskScheduler, never()).cancel(any());
    }
}
