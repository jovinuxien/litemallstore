package org.linlinjava.litemall.order.domain.model.agregates;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aftersale aggregate is the single transition source for RMA state:
 * applicability guards at apply time (ownership without existence leak, order
 * state, amount cap) and strictly guarded lifecycle transitions afterwards.
 */
class LitemallAftersaleAggregateTest {

    private static final LitemallUserId OWNER = new LitemallUserId(42);
    private static final LitemallUserId STRANGER = new LitemallUserId(43);

    private static LitemallOrderAggregate paidOrder(LitemallOrderStatus status) {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(77));
        order.setUserId(OWNER);
        order.setOrderSn("SN-77");
        order.setOrderStatus(status);
        order.setActualPrice(new LitemallMoney(new BigDecimal("85")));
        return order;
    }

    @Test
    void apply_onPaidOrder_opensApplication_defaultAmountIsPaid() {
        LitemallAftersaleAggregate aftersale = LitemallAftersaleAggregate.apply(
                paidOrder(LitemallOrderStatus.PAID), OWNER,
                (short) 2, "damaged", null, null, null);

        assertEquals(LitemallAfterSaleStatus.STATUS_REQUEST, aftersale.getStatus());
        assertEquals(0, aftersale.getAmount().getAmount().compareTo(new BigDecimal("85")));
        assertTrue(aftersale.isOpen());
    }

    @Test
    void apply_onReceivedOrder_isAllowed() {
        // The core RMA case: goods received, then returned.
        LitemallAftersaleAggregate aftersale = LitemallAftersaleAggregate.apply(
                paidOrder(LitemallOrderStatus.AUTO_DELIVERED), OWNER,
                (short) 2, "wrong size", new BigDecimal("50"), null, null);
        assertEquals(0, aftersale.getAmount().getAmount().compareTo(new BigDecimal("50")));
    }

    @Test
    void apply_byNonOwner_readsAsNotFound() {
        LitemallAftersaleException e = assertThrows(LitemallAftersaleException.class,
                () -> LitemallAftersaleAggregate.apply(paidOrder(LitemallOrderStatus.PAID),
                        STRANGER, (short) 0, "r", null, null, null));
        // No existence leak: the stranger cannot tell the order exists.
        assertEquals("Order not found", e.getMessage());
    }

    @Test
    void apply_onUnpaidOrRefundedOrder_isRejected() {
        assertThrows(LitemallAftersaleException.class,
                () -> LitemallAftersaleAggregate.apply(paidOrder(LitemallOrderStatus.CREATED),
                        OWNER, (short) 0, "r", null, null, null));
        assertThrows(LitemallAftersaleException.class,
                () -> LitemallAftersaleAggregate.apply(paidOrder(LitemallOrderStatus.REFUNDED),
                        OWNER, (short) 0, "r", null, null, null));
    }

    @Test
    void apply_amountAbovePaid_isRejected() {
        assertThrows(LitemallAftersaleException.class,
                () -> LitemallAftersaleAggregate.apply(paidOrder(LitemallOrderStatus.PAID),
                        OWNER, (short) 0, "r", new BigDecimal("85.01"), null, null));
    }

    @Test
    void lifecycle_approveThenRefund() {
        LitemallAftersaleAggregate aftersale = LitemallAftersaleAggregate.apply(
                paidOrder(LitemallOrderStatus.PAID), OWNER, (short) 1, "r", null, null, null);
        aftersale.approve();
        assertEquals(LitemallAfterSaleStatus.STATUS_RECEPT, aftersale.getStatus());
        aftersale.markRefunded();
        assertEquals(LitemallAfterSaleStatus.STATUS_REFUND, aftersale.getStatus());
        // Terminal: no further decisions.
        assertThrows(LitemallAftersaleException.class, aftersale::approve);
        assertThrows(LitemallAftersaleException.class, aftersale::reject);
        assertThrows(LitemallAftersaleException.class, () -> aftersale.cancel(OWNER));
    }

    @Test
    void lifecycle_rejectOnlyFromApplied() {
        LitemallAftersaleAggregate aftersale = LitemallAftersaleAggregate.apply(
                paidOrder(LitemallOrderStatus.PAID), OWNER, (short) 1, "r", null, null, null);
        aftersale.reject();
        assertEquals(LitemallAfterSaleStatus.STATUS_REJECT, aftersale.getStatus());
        assertThrows(LitemallAftersaleException.class, aftersale::approve);
    }

    @Test
    void cancel_onlyByOwner_andOnlyWhileUndecided() {
        LitemallAftersaleAggregate aftersale = LitemallAftersaleAggregate.apply(
                paidOrder(LitemallOrderStatus.PAID), OWNER, (short) 1, "r", null, null, null);
        assertThrows(LitemallAftersaleException.class, () -> aftersale.cancel(STRANGER));
        aftersale.cancel(OWNER);
        assertEquals(LitemallAfterSaleStatus.STATUS_CANCEL, aftersale.getStatus());
        assertThrows(LitemallAftersaleException.class, () -> aftersale.cancel(OWNER));
    }
}
