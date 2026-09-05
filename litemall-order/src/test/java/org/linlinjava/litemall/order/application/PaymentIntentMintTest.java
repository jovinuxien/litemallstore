package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.UnpaidOrderTaskScheduler;
import org.linlinjava.litemall.order.application.internal.cj.CjPlacementService;
import org.linlinjava.litemall.order.application.util.exception.payment.LitemallPaymentGatewayException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.PaymentGatewayPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentDraft;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.PaymentIntentState;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment.RefundOutcome;
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
 * Package A of plan-order-lifecycle-e2e.md on the client side of payment: the intent id is
 * recorded when minted (so the sweep can reconcile), a re-mint never leaves a second live
 * intent behind, and the client pay call is idempotent against a payment the webhook or the
 * sweep already settled.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentIntentMintTest {

    private static final LitemallOrderId ORDER = new LitemallOrderId(12);

    @Mock private LitemallOrderServiceImpl orderServiceImpl;
    @Mock private LitemallOrderRepository orderRepository;
    @Mock private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock private PaymentGatewayPort gateway;
    @Mock private LitemallOrderStatusHistoryRepository statusHistoryRepository;
    @Mock private LitemallDomainEventPublisher domainEventPublisher;
    @Mock private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;
    @Mock private CjPlacementService cjPlacementService;
    @Mock private org.linlinjava.litemall.order.application.internal.cj.CjOpsNotifier opsNotifier;

    private LitemallOrderOrchestratorService orchestrator;

    @BeforeEach
    void wire() {
        orchestrator = new LitemallOrderOrchestratorService(orderServiceImpl, orderRepository, grouponServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "paymentGatewayPort", gateway);
        ReflectionTestUtils.setField(orchestrator, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(orchestrator, "domainEventPublisher", domainEventPublisher);
        ReflectionTestUtils.setField(orchestrator, "unpaidOrderTaskScheduler", unpaidOrderTaskScheduler);
        ReflectionTestUtils.setField(orchestrator, "cjPlacementService", cjPlacementService);
        ReflectionTestUtils.setField(orchestrator, "opsNotifier", opsNotifier);
        when(gateway.createIntent(eq(12), any())).thenReturn(new PaymentIntentDraft("pi_new", "secret", 2500L, "eur"));
        when(orderRepository.recordPaymentIntentIfCreated(eq(ORDER), anyString())).thenReturn(1);
    }

    private LitemallOrderAggregate order(LitemallOrderStatus status, String recordedIntent) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(ORDER);
        order.setUserId(new LitemallUserId(42));
        order.setOrderSn("20260905000012");
        order.setOrderStatus(status);
        order.setActualPrice(new LitemallMoney(new BigDecimal("25.00")));
        order.setPaymentIntentId(recordedIntent);
        order.setSource(LitemallOrderAggregate.SOURCE_LOCAL);
        return order;
    }

    private static PaymentIntentState state(PaymentIntentState.Status status) {
        return PaymentIntentState.of(status, 12, status == PaymentIntentState.Status.SUCCEEDED ? 2500L : 0L, "eur", "");
    }

    // ------------------------------------------------------------------
    // createPaymentIntent
    // ------------------------------------------------------------------

    @Test
    void firstMint_recordsTheIntentOnTheOrder() {
        PaymentIntentDraft draft = orchestrator.createPaymentIntent(order(LitemallOrderStatus.CREATED, null));

        assertThat(draft.getPaymentIntentId()).isEqualTo("pi_new");
        verify(orderRepository).recordPaymentIntentIfCreated(ORDER, "pi_new");
        verify(gateway, never()).inspect(anyString());
    }

    @Test
    void remint_cancelsThePreviousPendingIntentFirst_soOnlyOneCanCapture() {
        when(gateway.inspect("pi_old")).thenReturn(state(PaymentIntentState.Status.PENDING));
        when(gateway.cancelIntent("pi_old")).thenReturn(state(PaymentIntentState.Status.CANCELED));

        PaymentIntentDraft draft = orchestrator.createPaymentIntent(order(LitemallOrderStatus.CREATED, "pi_old"));

        assertThat(draft.getPaymentIntentId()).isEqualTo("pi_new");
        var order = inOrder(gateway, orderRepository);
        order.verify(gateway).cancelIntent("pi_old");
        order.verify(gateway).createIntent(eq(12), any());
        order.verify(orderRepository).recordPaymentIntentIfCreated(ORDER, "pi_new");
    }

    /** Paid in another tab / a redirect the SPA never followed up: settle it, refuse a second charge. */
    @Test
    void remintWhenThePreviousIntentSucceeded_settlesAndRefusesToMint() {
        when(gateway.inspect("pi_old")).thenReturn(state(PaymentIntentState.Status.SUCCEEDED));
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.PAID, "pi_old")));

        assertThatThrownBy(() -> orchestrator.createPaymentIntent(order(LitemallOrderStatus.CREATED, "pi_old")))
                .isInstanceOf(LitemallPaymentGatewayException.class)
                .hasMessageContaining("already been paid");

        verify(orderServiceImpl).markOrderPaid(ORDER, "CREDIT_CARD:pi_old", "pi_old");
        verify(unpaidOrderTaskScheduler).cancel(ORDER);
        verify(gateway, never()).createIntent(anyInt(), any());
    }

    @Test
    void remintWhileThePreviousIntentIsProcessing_refusesWithoutMinting() {
        when(gateway.inspect("pi_old")).thenReturn(state(PaymentIntentState.Status.PROCESSING));

        assertThatThrownBy(() -> orchestrator.createPaymentIntent(order(LitemallOrderStatus.CREATED, "pi_old")))
                .isInstanceOf(LitemallPaymentGatewayException.class)
                .hasMessageContaining("processed");

        verify(gateway, never()).createIntent(anyInt(), any());
        verify(orderServiceImpl, never()).markOrderPaid(any(), anyString(), anyString());
    }

    /** The order left CREATED while Stripe was minting: the fresh intent is cancelled, not handed out. */
    @Test
    void mintOnAnOrderThatJustLeftCreated_cancelsTheFreshIntentAndRefuses() {
        when(orderRepository.recordPaymentIntentIfCreated(ORDER, "pi_new")).thenReturn(0);

        assertThatThrownBy(() -> orchestrator.createPaymentIntent(order(LitemallOrderStatus.CREATED, null)))
                .isInstanceOf(LitemallPaymentGatewayException.class);

        verify(gateway).cancelIntent("pi_new");
    }

    @Test
    void mintOnANonPayableOrder_refusesBeforeTalkingToStripe() {
        assertThatThrownBy(() -> orchestrator.createPaymentIntent(order(LitemallOrderStatus.PAID, "pi_old")))
                .isInstanceOf(LitemallPaymentGatewayException.class);

        verifyNoMoreInteractions(gateway);
    }

    // ------------------------------------------------------------------
    // /actions/pay against an order the webhook or sweep already settled
    // ------------------------------------------------------------------

    private LitemallOrderPaymentCommand cardPay(String intent) {
        return new LitemallOrderPaymentCommand(ORDER, new LitemallUserId(42), PaymentMethod.CREDIT_CARD, intent);
    }

    @Test
    void clientPayAfterTheWebhookSettledTheSameIntent_isSuccessNotInvalidState() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.PAID, "pi_same")));

        LitemallOrderOperationResult result = orchestrator.payOrder(cardPay("pi_same"));

        assertThat(result.isSuccess()).isTrue();
        verify(orderServiceImpl, never()).markOrderPaid(any(), anyString(), anyString());
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void clientPayOnAnAutoCancelledOrderWithACapturedIntent_refundsAndSaysSo() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_late")));
        when(gateway.inspect("pi_late")).thenReturn(state(PaymentIntentState.Status.SUCCEEDED));
        when(gateway.refund(eq("pi_late"), any(), eq(12), eq("pi_late"))).thenReturn(RefundOutcome.succeeded("re_1"));

        LitemallOrderOperationResult result = orchestrator.payOrder(cardPay("pi_late"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("refunded automatically");
        verify(gateway).refund(eq("pi_late"), any(), eq(12), eq("pi_late"));
    }

    @Test
    void clientPayOnACancelledOrderWithNoCapture_isStillInvalidState() {
        when(orderRepository.findById(ORDER)).thenReturn(Optional.of(order(LitemallOrderStatus.SYSTEM_CANCELED, "pi_x")));
        when(gateway.inspect("pi_x")).thenReturn(state(PaymentIntentState.Status.CANCELED));

        LitemallOrderOperationResult result = orchestrator.payOrder(cardPay("pi_x"));

        assertThat(result.isSuccess()).isFalse();
        verify(gateway, never()).refund(anyString(), any(), anyInt(), anyString());
    }
}
