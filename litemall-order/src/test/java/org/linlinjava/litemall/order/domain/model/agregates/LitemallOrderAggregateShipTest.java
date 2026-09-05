package org.linlinjava.litemall.order.domain.model.agregates;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;

import static org.assertj.core.api.Assertions.assertThat;

/** F10 + F12 of plan-order-lifecycle-e2e.md at the aggregate: honest tracking + honest operator. */
class LitemallOrderAggregateShipTest {

    private static LitemallOrderAggregate paid() {
        LitemallOrderAggregate order = new LitemallOrderAggregate();
        order.setOrderId(new LitemallOrderId(5));
        order.setOrderStatus(LitemallOrderStatus.PAID);
        return order;
    }

    @Test
    void shipWithTracking_recordsNumberAndOperator() {
        LitemallOrderAggregate order = paid();

        order.ship("CJPacket", "CJ123", "system");

        assertThat(order.getOrderStatus()).isEqualTo(LitemallOrderStatus.SHIPPED);
        assertThat(order.getShipSn()).isEqualTo("CJ123");
        LitemallOrderStatusChange hop = order.getStatusChanges().get(0);
        assertThat(hop.getOperator()).isEqualTo("system");
        assertThat(hop.getChangeMessage()).isEqualTo("Shipped via CJPacket (CJ123)");
    }

    @Test
    void shipWithoutTracking_leavesNumberNullAndSaysPending() {
        LitemallOrderAggregate order = paid();

        order.ship("CJPacket", "", "system");

        assertThat(order.getOrderStatus()).isEqualTo(LitemallOrderStatus.SHIPPED);
        assertThat(order.getShipSn()).isNull();
        assertThat(order.getStatusChanges().get(0).getChangeMessage())
                .isEqualTo("Shipped via CJPacket — tracking number pending")
                .doesNotContain("()");
    }

    @Test
    void legacyTwoArgShip_isStillTheAdminForm() {
        LitemallOrderAggregate order = paid();

        order.ship("PostNord", "PN1");

        assertThat(order.getStatusChanges().get(0).getOperator()).isEqualTo("admin");
    }
}
