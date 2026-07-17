package org.linlinjava.litemall.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.order.application.internal.LitemallGrouponServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallOrderServiceImpl;
import org.linlinjava.litemall.order.application.internal.UnpaidOrderTaskScheduler;
import org.linlinjava.litemall.order.application.internal.cj.CjFulfillmentService;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.commands.payment.LitemallOrderPaymentCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pay-first CJ fulfillment on {@link LitemallOrderOrchestratorService#payOrder}: a
 * {@code source='cj'} order is replayed to CJ exactly once the payment succeeds, a CJ
 * rejection aborts the payment (exception propagates so the transaction rolls back and
 * no post-payment notification/event leaks), and a local order never touches CJ.
 */
@ExtendWith(MockitoExtension.class)
class CjOrderPayFlowTest {

    @Mock
    private LitemallOrderServiceImpl orderServiceImpl;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallGrouponServiceLayer grouponServiceLayer;
    @Mock
    private LitemallIWalletService walletService;
    @Mock
    private CjFulfillmentService cjFulfillmentService;
    @Mock
    private LitemallOrderGoodsRepository orderGoodsRepository;
    @Mock
    private NotifyService notifyService;
    @Mock
    private UnpaidOrderTaskScheduler unpaidOrderTaskScheduler;
    @Mock
    private LitemallDomainEventPublisher domainEventPublisher;

    private LitemallOrderOrchestratorService orchestrator;

    @BeforeEach
    void wireFieldInjectedDeps() {
        orchestrator = new LitemallOrderOrchestratorService(
                orderServiceImpl, orderRepository, grouponServiceLayer);
        ReflectionTestUtils.setField(orchestrator, "walletService", walletService);
        ReflectionTestUtils.setField(orchestrator, "cjFulfillmentService", cjFulfillmentService);
        ReflectionTestUtils.setField(orchestrator, "orderGoodsRepository", orderGoodsRepository);
        ReflectionTestUtils.setField(orchestrator, "notifyService", notifyService);
        ReflectionTestUtils.setField(orchestrator, "unpaidOrderTaskScheduler", unpaidOrderTaskScheduler);
        ReflectionTestUtils.setField(orchestrator, "domainEventPublisher", domainEventPublisher);
    }

    private LitemallOrderAggregate unpaidOrder(int id, String source) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(id));
        order.setUserId(new LitemallUserId(42));
        // long enough for the SMS template's orderSn.substring(8, 14)
        order.setOrderSn("2026070412345" + id);
        order.setMobile("0700000000");
        order.setOrderStatus(LitemallOrderStatus.CREATED);
        order.setActualPrice(new LitemallMoney(new BigDecimal("25.00")));
        order.setSource(source);
        return order;
    }

    private LitemallOrderPaymentCommand walletPay(int orderId) {
        return new LitemallOrderPaymentCommand(
                new LitemallOrderId(orderId), new LitemallUserId(42), PaymentMethod.WALLET, null);
    }

    @Test
    void cjOrder_walletPaySuccess_placesAtCjWithTheOrderLines() {
        LitemallOrderId orderId = new LitemallOrderId(51);
        LitemallOrderAggregate order = unpaidOrder(51, LitemallOrderAggregate.SOURCE_CJ);
        List<LitemallOrderGoodsAggregate> lines = List.of(new LitemallOrderGoodsAggregate());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(orderId)).thenReturn(lines);

        LitemallOrderOperationResult result = orchestrator.payOrder(walletPay(51));

        assertTrue(result.isSuccess());
        verify(walletService).debit(any());
        verify(orderServiceImpl).markOrderPaid(eq(orderId), anyString(), any());
        verify(cjFulfillmentService).placeForPaidOrder(order, lines);
    }

    @Test
    void cjRejection_abortsThePayment_beforeAnyPostPaymentSideEffect() {
        LitemallOrderId orderId = new LitemallOrderId(52);
        LitemallOrderAggregate order = unpaidOrder(52, LitemallOrderAggregate.SOURCE_CJ);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(cjFulfillmentService.placeForPaidOrder(eq(order), anyList()))
                .thenThrow(new LitemallCjOrderException("shipping country not supported"));

        // Propagating out of the @Transactional orchestrator rolls back the wallet
        // debit and the PAID transition; the REST layer maps it to a clean pay-failed.
        assertThrows(LitemallCjOrderException.class, () -> orchestrator.payOrder(walletPay(52)));

        verify(notifyService, never()).notifyMail(anyString(), anyString());
        verify(unpaidOrderTaskScheduler, never()).cancel(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    void localOrder_walletPay_neverTouchesCj() {
        LitemallOrderId orderId = new LitemallOrderId(53);
        LitemallOrderAggregate order = unpaidOrder(53, LitemallOrderAggregate.SOURCE_LOCAL);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        LitemallOrderOperationResult result = orchestrator.payOrder(walletPay(53));

        assertTrue(result.isSuccess());
        verify(cjFulfillmentService, never()).placeForPaidOrder(any(), anyList());
    }

    @Test
    void legacyOrderWithNullSource_walletPay_neverTouchesCj() {
        // Pre-V27 rows read source=null through older snapshots; only an explicit
        // 'cj' may route to CJ.
        LitemallOrderId orderId = new LitemallOrderId(54);
        LitemallOrderAggregate order = unpaidOrder(54, null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        LitemallOrderOperationResult result = orchestrator.payOrder(walletPay(54));

        assertTrue(result.isSuccess());
        verify(cjFulfillmentService, never()).placeForPaidOrder(any(), anyList());
        verify(orderServiceImpl).markOrderPaid(eq(orderId), anyString(), any());
    }
}
