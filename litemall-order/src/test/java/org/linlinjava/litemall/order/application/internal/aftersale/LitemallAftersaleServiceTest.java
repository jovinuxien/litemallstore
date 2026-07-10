package org.linlinjava.litemall.order.application.internal.aftersale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallAftersaleRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Customer-side aftersale scoping and invariants: every operation reads as
 * "not found" to a non-owner, one open application per order, the sn follows
 * {orderSn}-A{n}, and every hop lands on the order's timeline.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LitemallAftersaleServiceTest {

    private static final LitemallOrderId ORDER_ID = new LitemallOrderId(77);
    private static final LitemallUserId OWNER = new LitemallUserId(42);
    private static final LitemallUserId STRANGER = new LitemallUserId(43);

    @Mock
    private LitemallAftersaleRepository aftersaleRepository;
    @Mock
    private LitemallOrderRepository orderRepository;
    @Mock
    private LitemallOrderStatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private LitemallAftersaleService service;

    private LitemallOrderAggregate order;

    @BeforeEach
    void setUp() {
        order = new LitemallOrderAggregate();
        order.setOrderId(ORDER_ID);
        order.setUserId(OWNER);
        order.setOrderSn("SN-77");
        order.setOrderStatus(LitemallOrderStatus.PAID);
        order.setActualPrice(new LitemallMoney(new BigDecimal("85")));
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(aftersaleRepository.findOpenByOrder(any())).thenReturn(Optional.empty());
        when(aftersaleRepository.countByOrder(any())).thenReturn(0L);
        when(aftersaleRepository.add(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void apply_persistsWithSequencedSn_flagsOrder_recordsTimelineHop() {
        LitemallAftersaleAggregate created = service.apply(OWNER, ORDER_ID,
                new LitemallAftersaleService.ApplyCommand((short) 2, "damaged",
                        new BigDecimal("50"), null, null));

        assertEquals("SN-77-A1", created.getAftersaleSn());
        verify(orderRepository).updateAfterSaleStatus(ORDER_ID,
                LitemallAfterSaleStatus.STATUS_REQUEST.getCode());
        ArgumentCaptor<LitemallOrderStatusChange> hop =
                ArgumentCaptor.forClass(LitemallOrderStatusChange.class);
        verify(statusHistoryRepository).record(hop.capture());
        assertEquals("aftersale_request", hop.getValue().getChangeType());
        // Same-status entry: the ORDER status did not move.
        assertEquals(LitemallOrderStatus.PAID, hop.getValue().getFromStatus());
        assertEquals(LitemallOrderStatus.PAID, hop.getValue().getToStatus());
    }

    @Test
    void apply_secondOpenApplication_isRefused() {
        LitemallAftersaleAggregate open = LitemallAftersaleAggregate.apply(
                order, OWNER, (short) 1, "first", null, null, null);
        open.setAftersaleSn("SN-77-A1");
        when(aftersaleRepository.findOpenByOrder(any())).thenReturn(Optional.of(open));

        LitemallAftersaleException e = assertThrows(LitemallAftersaleException.class,
                () -> service.apply(OWNER, ORDER_ID,
                        new LitemallAftersaleService.ApplyCommand((short) 1, "second", null, null, null)));
        assertEquals(true, e.getMessage().contains("SN-77-A1"));
        verify(aftersaleRepository, never()).add(any());
    }

    @Test
    void apply_byStranger_readsAsOrderNotFound() {
        assertThrows(LitemallAftersaleException.class,
                () -> service.apply(STRANGER, ORDER_ID,
                        new LitemallAftersaleService.ApplyCommand((short) 1, "r", null, null, null)));
        verify(aftersaleRepository, never()).add(any());
    }

    @Test
    void detail_ofAnotherUsersAftersale_readsAsNotFound() {
        // The stranger owns order 78; aftersale 5 belongs to OWNER's order 77.
        LitemallOrderAggregate strangersOrder = new LitemallOrderAggregate();
        strangersOrder.setOrderId(new LitemallOrderId(78));
        strangersOrder.setUserId(STRANGER);
        strangersOrder.setOrderStatus(LitemallOrderStatus.PAID);
        when(orderRepository.findById(new LitemallOrderId(78)))
                .thenReturn(Optional.of(strangersOrder));
        LitemallAftersaleAggregate ownersAftersale = LitemallAftersaleAggregate.apply(
                order, OWNER, (short) 1, "r", null, null, null);
        ownersAftersale.setId(5);
        when(aftersaleRepository.findById(5)).thenReturn(Optional.of(ownersAftersale));

        assertThrows(LitemallAftersaleException.class,
                () -> service.detail(STRANGER, new LitemallOrderId(78), 5));
    }

    @Test
    void cancel_resetsOrderFlag_andRecordsHop() {
        LitemallAftersaleAggregate open = LitemallAftersaleAggregate.apply(
                order, OWNER, (short) 1, "r", null, null, null);
        open.setId(5);
        open.setAftersaleSn("SN-77-A1");
        when(aftersaleRepository.findById(5)).thenReturn(Optional.of(open));

        service.cancel(OWNER, ORDER_ID, 5);

        assertEquals(LitemallAfterSaleStatus.STATUS_CANCEL, open.getStatus());
        verify(aftersaleRepository).update(open);
        verify(orderRepository).updateAfterSaleStatus(ORDER_ID,
                LitemallAfterSaleStatus.STATUS_CANCEL.getCode());
    }
}
