package org.linlinjava.litemall.order.domain.model.util;

import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;

import java.util.Collections;
import java.util.List;

public class LitemallOrderStatusQuery {

    public static List<LitemallOrderStatus> getStatusesForShowType(int showType) {
        switch (showType) {
            case 1: return List.of(LitemallOrderStatus.CREATED);
            case 2: return List.of(LitemallOrderStatus.PAID);
            case 3: return List.of(LitemallOrderStatus.SHIPPED);
            case 4: return List.of(LitemallOrderStatus.DELIVERED);
            default: return Collections.emptyList();
        }
    }

    public static boolean isCreateStatus(LitemallOrder order) {
        return order.getOrderStatus() == intToShort(LitemallOrderStatus.CREATED.getCode());
    }

    public static boolean hasPayed(LitemallOrder order) {
        return order.getOrderStatus() != intToShort(LitemallOrderStatus.CREATED.getCode())
                && order.getOrderStatus() != intToShort(LitemallOrderStatus.CANCELLED.getCode())
                && order.getOrderStatus() != intToShort(LitemallOrderStatus.SYSTEM_CANCELLED.getCode());
    }

    private static Short intToShort(int status) {
        return (short) status;
    }
}
