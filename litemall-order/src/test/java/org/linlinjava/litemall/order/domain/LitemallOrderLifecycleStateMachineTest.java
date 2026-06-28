package org.linlinjava.litemall.order.domain;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.util.LitemallOrderStatusQuery;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus.*;

/**
 * Pure (no Spring/DB) checks of the order lifecycle state machine:
 * the canonical transition graph, the single-source-of-truth delegation, and that each
 * aggregate mutator applies the transition, records a history entry, and rejects illegal
 * source states.
 */
class LitemallOrderLifecycleStateMachineTest {

    // ---- canonical transition graph -------------------------------------------------

    @Test
    void createdCanOnlyGoToPaidOrCancelled() {
        assertTrue(CREATED.canTransitionTo(PAID));
        assertTrue(CREATED.canTransitionTo(CANCELED));
        assertTrue(CREATED.canTransitionTo(SYSTEM_CANCELED));
        assertFalse(CREATED.canTransitionTo(SHIPPED));
        assertFalse(CREATED.canTransitionTo(REFUNDED));
    }

    @Test
    void paidGoesToShippedOrRefundRequest_neverHardCancel() {
        assertTrue(PAID.canTransitionTo(SHIPPED));
        assertTrue(PAID.canTransitionTo(REFUND_REQUEST));
        // policy: a paid order is unwound via refund, not hard-cancel
        assertFalse(PAID.canTransitionTo(CANCELED));
        assertFalse(PAID.canTransitionTo(SYSTEM_CANCELED));
    }

    @Test
    void shippedGoesToDeliveredAutoDeliveredOrRefundRequest() {
        assertTrue(SHIPPED.canTransitionTo(DELIVERED));
        assertTrue(SHIPPED.canTransitionTo(AUTO_DELIVERED));
        assertTrue(SHIPPED.canTransitionTo(REFUND_REQUEST));
        assertFalse(SHIPPED.canTransitionTo(PAID));
    }

    @Test
    void refundRequestGoesToRefunded_andTerminalsGoNowhere() {
        assertTrue(REFUND_REQUEST.canTransitionTo(REFUNDED));
        for (LitemallOrderStatus terminal : new LitemallOrderStatus[]{
                DELIVERED, AUTO_DELIVERED, CANCELED, SYSTEM_CANCELED, REFUNDED}) {
            for (LitemallOrderStatus to : LitemallOrderStatus.values()) {
                assertFalse(terminal.canTransitionTo(to),
                        terminal + " must be terminal but allowed " + to);
            }
        }
    }

    @Test
    void isValidTransitionDelegatesToCanTransitionTo() {
        for (LitemallOrderStatus from : LitemallOrderStatus.values()) {
            for (LitemallOrderStatus to : LitemallOrderStatus.values()) {
                assertEquals(from.canTransitionTo(to),
                        LitemallOrderStatusQuery.isValidTransition(from, to),
                        from + "->" + to + " disagreed between the two rule sources");
            }
        }
    }

    // ---- aggregate mutators record history + emit events ----------------------------

    private LitemallOrderAggregate orderIn(LitemallOrderStatus status) {
        LitemallOrderAggregate agg = new LitemallOrderAggregate();
        agg.setOrderId(new LitemallOrderId(42));
        agg.setOrderStatus(status);
        return agg;
    }

    @Test
    void markAsPaidAppliesTransitionAndRecordsHistory() {
        LitemallOrderAggregate agg = orderIn(CREATED);
        agg.markAsPaid();
        assertEquals(PAID, agg.getOrderStatus());
        assertNotNull(agg.getPayTime());
        assertEquals(1, agg.getStatusChanges().size());
        LitemallOrderStatusChange change = agg.getStatusChanges().get(0);
        assertEquals(CREATED, change.getFromStatus());
        assertEquals(PAID, change.getToStatus());
        assertEquals("pay", change.getChangeType());
        assertFalse(agg.getDomainEvents().isEmpty());
    }

    @Test
    void shipRecordsChannelAndTracking() {
        LitemallOrderAggregate agg = orderIn(PAID);
        agg.ship("SF", "SF123456");
        assertEquals(SHIPPED, agg.getOrderStatus());
        assertEquals("SF", agg.getShipChannel());
        assertEquals("SF123456", agg.getShipSn());
        assertNotNull(agg.getShipTime());
        assertEquals("ship", agg.getStatusChanges().get(0).getChangeType());
    }

    @Test
    void confirmDeliverySetsConfirmTime() {
        LitemallOrderAggregate agg = orderIn(SHIPPED);
        agg.confirmDelivery();
        assertEquals(DELIVERED, agg.getOrderStatus());
        assertNotNull(agg.getConfirmTime());
    }

    @Test
    void refundFlow() {
        LitemallOrderAggregate agg = orderIn(PAID);
        agg.requestRefund("changed my mind");
        assertEquals(REFUND_REQUEST, agg.getOrderStatus());
        agg.refund(new LitemallMoney(new BigDecimal("12.34")));
        assertEquals(REFUNDED, agg.getOrderStatus());
        assertEquals(new BigDecimal("12.34"), agg.getRefundAmount().getAmount());
        assertNotNull(agg.getRefundTime());
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThrows(IllegalStateException.class, () -> orderIn(CREATED).ship("SF", "X"));
        assertThrows(IllegalStateException.class, () -> orderIn(DELIVERED).cancel("nope"));
        assertThrows(IllegalStateException.class, () -> orderIn(PAID).confirmDelivery());
        assertThrows(IllegalStateException.class, () -> orderIn(SHIPPED).markAsPaid());
    }

    @Test
    void handleOptionMatchesReachableActions() {
        // paid → refund offered, cancel/pay not
        LitemallOrderHandleOption paid = LitemallOrderHandleOption.forStatus(PAID);
        assertTrue(paid.isRefund());
        assertFalse(paid.isCancel());
        assertFalse(paid.isPay());
        // shipped → confirm AND refund
        LitemallOrderHandleOption shipped = LitemallOrderHandleOption.forStatus(SHIPPED);
        assertTrue(shipped.isConfirm());
        assertTrue(shipped.isRefund());
    }
}
