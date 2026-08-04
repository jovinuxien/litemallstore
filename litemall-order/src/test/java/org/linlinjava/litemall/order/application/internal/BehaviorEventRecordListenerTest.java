package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallUserEventMapper;
import org.linlinjava.litemall.db.domain.LitemallUserEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderPaidEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderRefundedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderGoodsRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase-0 coverage for the paid → purchase / refunded → refund event rows:
 * deterministic event ids, server origin, item-line payload, the disabled
 * kill-switch, and the order-missing WARN path. The listener hands work to its
 * single daemon worker, so outcomes are asserted with
 * {@code verify(..., timeout(...))} / {@code after(...)}.
 */
class BehaviorEventRecordListenerTest {

    private static final int VERIFY_TIMEOUT_MS = 5000;

    private final LitemallOrderRepository orderRepository = mock(LitemallOrderRepository.class);
    private final LitemallOrderGoodsRepository orderGoodsRepository = mock(LitemallOrderGoodsRepository.class);
    private final LitemallUserEventMapper userEventMapper = mock(LitemallUserEventMapper.class);

    private BehaviorEventRecordListener listener(boolean enabled) {
        return new BehaviorEventRecordListener(orderRepository, orderGoodsRepository, userEventMapper, enabled);
    }

    private static LitemallOrderAggregate order(int orderId) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(orderId));
        order.setUserId(new LitemallUserId(7));
        order.setOrderSn("20260804000042");
        order.setActualPrice(new LitemallMoney(new BigDecimal("44.16")));
        return order;
    }

    private static LitemallOrderGoodsAggregate line(int goodsId, int productId, int number, String price) {
        LitemallOrderGoodsAggregate goods = new LitemallOrderGoodsAggregate();
        goods.setGoodsId(new LitemallGoodsId(goodsId));
        goods.setProductId(new LitemallGoodsProductId(productId));
        goods.setNumber((short) number);
        goods.setPrice(new LitemallMoney(new BigDecimal(price)));
        return goods;
    }

    @Test
    void paidOrderWritesServerOriginPurchaseRowWithItemLines() {
        LitemallOrderAggregate order = order(107);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of(line(10000553, 33, 2, "19.98")));

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(107)));

        ArgumentCaptor<LitemallUserEvent> captor = ArgumentCaptor.forClass(LitemallUserEvent.class);
        verify(userEventMapper, timeout(VERIFY_TIMEOUT_MS)).insertIgnore(captor.capture());
        LitemallUserEvent row = captor.getValue();
        assertThat(row.getEventType()).isEqualTo(LitemallUserEvent.TYPE_PURCHASE);
        assertThat(row.getOrigin()).isEqualTo(LitemallUserEvent.ORIGIN_SERVER);
        assertThat(row.getUserId()).isEqualTo(7);
        assertThat(row.getVisitorId()).isNull();
        assertThat(row.getEventId()).matches("[0-9a-f-]{36}");
        assertThat(row.getPayload())
                .contains("\"orderSn\":\"20260804000042\"")
                .contains("\"total\":44.16")
                .contains("\"goodsId\":10000553")
                .contains("\"qty\":2");
    }

    @Test
    void sameOrderAlwaysYieldsTheSameEventId() {
        LitemallOrderAggregate order = order(107);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of());

        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(107)));
        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(107)));

        ArgumentCaptor<LitemallUserEvent> captor = ArgumentCaptor.forClass(LitemallUserEvent.class);
        verify(userEventMapper, timeout(VERIFY_TIMEOUT_MS).times(2)).insertIgnore(captor.capture());
        assertThat(captor.getAllValues().get(0).getEventId())
                .isEqualTo(captor.getAllValues().get(1).getEventId());
    }

    @Test
    void refundWritesRefundRowPreferringRefundAmount() {
        LitemallOrderAggregate order = order(108);
        order.setRefundAmount(new LitemallMoney(new BigDecimal("12.50")));
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        listener(true).onOrderRefunded(new LitemallOrderRefundedEvent(new LitemallOrderId(108)));

        ArgumentCaptor<LitemallUserEvent> captor = ArgumentCaptor.forClass(LitemallUserEvent.class);
        verify(userEventMapper, timeout(VERIFY_TIMEOUT_MS)).insertIgnore(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(LitemallUserEvent.TYPE_REFUND);
        assertThat(captor.getValue().getPayload()).contains("\"amount\":12.50");
    }

    @Test
    void disabledKillSwitchWritesNothing() {
        when(orderRepository.findById(any())).thenReturn(Optional.of(order(107)));
        listener(false).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(107)));
        verify(userEventMapper, after(500).never()).insertIgnore(any());
    }

    @Test
    void missingOrderAndMapperFailureNeverThrow() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());
        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(999)));
        verify(userEventMapper, after(500).never()).insertIgnore(any());

        when(orderRepository.findById(any())).thenReturn(Optional.of(order(107)));
        when(orderGoodsRepository.findByOId(any())).thenReturn(List.of());
        when(userEventMapper.insertIgnore(any())).thenThrow(new RuntimeException("db down"));
        listener(true).onOrderPaid(new LitemallOrderPaidEvent(new LitemallOrderId(107)));
        verify(userEventMapper, timeout(VERIFY_TIMEOUT_MS)).insertIgnore(any());
        // no exception surfaced — the worker swallowed it (asserted implicitly by reaching here)
    }
}
