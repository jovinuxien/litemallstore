package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAddressRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallGrouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F3 (lifecycle follow-up, 2026-09): the stock reserved for a cancelled order goes
 * back on sale only once the cancellation COMMITS. The restore is a remote call that
 * commits in goods-management on its own, so an inline call followed by a local
 * rollback (CJ cancel, history persist, event publish) would leave the order
 * CREATED with its quantities also restored — oversold by exactly the order.
 *
 * <p>The first tests in this module that drive transaction synchronizations: they
 * pin the semantics (deferred until after-commit, never on rollback), not the mock
 * wiring. With no synchronization active the hook calls through inline — the
 * pattern the coupon/pink release hooks in the same methods already follow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallOrderCancelStockRestoreTest {

    private static final int PRODUCT_A = 501;
    private static final int PRODUCT_B = 502;

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

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private LitemallOrderAggregate createdOrder() {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(77));
        order.setUserId(new LitemallUserId(99));
        order.setOrderSn("SN-1");
        order.setOrderStatus(LitemallOrderStatus.CREATED);
        order.setAfterSaleStatus(LitemallAfterSaleStatus.STATUS_INIT);
        order.setCouponPrice(new LitemallMoney(BigDecimal.ZERO));
        return order;
    }

    private static LitemallOrderGoodsAggregate line(int productId, int qty) {
        LitemallOrderGoodsAggregate g = new LitemallOrderGoodsAggregate();
        g.setOrderId(new LitemallOrderId(77));
        g.setProductId(new LitemallGoodsProductId(productId));
        g.setNumber((short) qty);
        return g;
    }

    /** Two lines on product A (summed) + one on product B. */
    private void wire(LitemallOrderAggregate order) {
        ReflectionTestUtils.setField(service, "goodsFacade", goodsFacade);
        ReflectionTestUtils.setField(service, "promotionFacade", promotionFacade);
        ReflectionTestUtils.setField(service, "statusHistoryRepository", statusHistoryRepository);
        ReflectionTestUtils.setField(service, "cjFulfillmentService", cjFulfillmentService);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderRepository.markCanceledIfCreated(any())).thenReturn(1);
        when(orderRepository.markSystemCanceledIfCreated(any())).thenReturn(1);
        when(orderGoodsRepository.findByOId(any()))
                .thenReturn(List.of(line(PRODUCT_A, 2), line(PRODUCT_B, 1), line(PRODUCT_A, 3)));
    }

    private static final Map<Integer, Integer> EXPECTED = Map.of(PRODUCT_A, 5, PRODUCT_B, 1);

    // ---- no transaction synchronization: inline, the existing pattern -------------

    @Test
    void customerCancel_withoutTransaction_restoresInlineWithSummedQuantities() {
        wire(createdOrder());

        service.cancelOrder(new LitemallOrderId(77), "changed my mind");

        verify(goodsFacade).restoreStock(EXPECTED);
    }

    @Test
    void unpaidTimeoutAutoCancel_withoutTransaction_restoresInlineWithSummedQuantities() {
        wire(createdOrder());

        service.autoCancelOrder(new LitemallOrderId(77), "unpaid timeout");

        verify(goodsFacade).restoreStock(EXPECTED);
    }

    @Test
    void cancelWithNoLines_neverCallsRestore() {
        wire(createdOrder());
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of());

        service.cancelOrder(new LitemallOrderId(77), "changed my mind");

        verify(goodsFacade, never()).restoreStock(any());
    }

    // ---- inside a transaction: deferred until commit, never on rollback ----------

    @Test
    void customerCancel_insideTransaction_restoresOnlyAfterCommit() {
        wire(createdOrder());
        TransactionSynchronizationManager.initSynchronization();

        service.cancelOrder(new LitemallOrderId(77), "changed my mind");

        // Nothing has committed yet: the remote restore must not have happened.
        verify(goodsFacade, never()).restoreStock(any());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size(),
                "exactly one hook registered (coupon 0 and no pinkId register none)");

        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
        verify(goodsFacade).restoreStock(EXPECTED);
    }

    @Test
    void unpaidTimeoutAutoCancel_insideTransaction_restoresOnlyAfterCommit() {
        wire(createdOrder());
        TransactionSynchronizationManager.initSynchronization();

        service.autoCancelOrder(new LitemallOrderId(77), "unpaid timeout");

        verify(goodsFacade, never()).restoreStock(any());
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
        verify(goodsFacade).restoreStock(EXPECTED);
    }

    @Test
    void cancelThatRollsBack_neverRestoresStock() {
        wire(createdOrder());
        TransactionSynchronizationManager.initSynchronization();

        service.cancelOrder(new LitemallOrderId(77), "changed my mind");

        // Drive the synchronizations the way a rolled-back transaction does:
        // afterCompletion(STATUS_ROLLED_BACK) and NO afterCommit.
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(goodsFacade, never()).restoreStock(any());
    }
}
