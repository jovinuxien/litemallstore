package org.linlinjava.litemall.db.util;

import org.linlinjava.litemall.db.domain.LitemallOrder;

import java.util.ArrayList;
import java.util.List;

/*
 * Order process：ORDER PLACED SUCCESSFULLY－》PAY ORDER－》SHIPPING－》RECEIPT OF GOODS
 * Order status：
 *   101 ORDER GENERATION NOT PAID,
 *   102 CANCELLATION BY USER WHO PLACED THE ORDER BUT NOT PAID,
 *   103 THE SYSTEM WILL AUTOMATICALLY CANCEL THE ORDER IF IT IS NOT PAID AND IS OVERDUE.
 *
 *   201 PAYMENT COMPLETED，THE MERCHANT HAS NOT SHIPPED THE GOODS；
 *   202 Order production, Paid but not shipped，User applies for refund；
 *   203 Administrator performs refund operation，Confirm refund is successful；
 *
 *   301 Merchant ships，User has not confirmed；
 *
 *   401 User confirms receipt，Order ends；
 *   402 User did not confirm receipt，However,
 *       after the express delivery feedback has been received，more than a certain time，
 *       The system automatically confirms receipt，Order ends。
 *
 * 当101用户未付款时，此时用户可以进行的操作是取消或者付款
 * 当201支付完成而商家未发货时，此时用户可以退款
 * 当301商家已发货时，此时用户可以有确认收货
 * 当401用户确认收货以后，此时用户可以进行的操作是退货、删除、去评价或者再次购买
 * 当402系统自动确认收货以后，此时用户可以删除、去评价、或者再次购买
 */
public class OrderUtil {

    public static final Short STATUS_CREATE = 101;
    public static final Short STATUS_PAY = 201;
    public static final Short STATUS_SHIP = 301;
    public static final Short STATUS_CONFIRM = 401;
    public static final Short STATUS_CANCEL = 102;
    public static final Short STATUS_AUTO_CANCEL = 103;
    public static final Short STATUS_ADMIN_CANCEL = 104;
    public static final Short STATUS_REFUND = 202;
    public static final Short STATUS_REFUND_CONFIRM = 203;
    public static final Short STATUS_AUTO_CONFIRM = 402;

    public static String orderStatusText(LitemallOrder order) {
        int status = order.getOrderStatus().intValue();

        if (status == 101) {
            return "UNPAID";
        }

        if (status == 102) {
            return "CANCELED";
        }

        if (status == 103) {
            return "CANCELED(SYSTEM)";
        }

        if (status == 201) {
            return "PAID";
        }

        if (status == 202) {
            return "ORDER CANCELLATION，REFUND IN PROGRESS";
        }

        if (status == 203) {
            return "REFUNDED";
        }

        if (status == 204) {
            return "GROUP PURCHASE HAS EXPIRED";
        }

        if (status == 301) {
            return "SHIPPED";
        }

        if (status == 401) {
            return "GOODS RECEIVED";
        }

        if (status == 402) {
            return "GOODS RECEIVED(SYSTEM)";
        }

        throw new IllegalStateException("ORDER STATUS NOT SUPPORTED");
    }


    public static OrderHandleOption build(LitemallOrder order) {
        int status = order.getOrderStatus().intValue();
        OrderHandleOption handleOption = new OrderHandleOption();

        if (status == 101) {
            // If the order has not been canceled and has not been paid, it can be paid and can be cancelled.
            handleOption.setCancel(true);
            handleOption.setPay(true);
        } else if (status == 102 || status == 103) {
            // If the order has been canceled or completed, it can be deleted
            handleOption.setDelete(true);
        } else if (status == 201) {
            // If the order has been paid but not shipped, a refund is available
            handleOption.setRefund(true);
        } else if (status == 202 || status == 204) {
            // If the order is being applied for refund, there is no relevant operation
        } else if (status == 203) {
            // If the order has been refunded, it can be deleted
            handleOption.setDelete(true);
        } else if (status == 301) {
            // If the order has been shipped but the goods have not been received, the goods can be received.
            // The order cannot be canceled at this time
            handleOption.setConfirm(true);
        } else if (status == 401 || status == 402) {
            // If the order has BEEN PAID and the goods have BEEN RECEIVED, you can
            //         DELETE IT,
            //         LEAVE COMMENTS,
            //         APPLY for AFTER-SALES service and
            //         PURCHASE AGAIN.
            handleOption.setDelete(true);
            handleOption.setComment(true);
            handleOption.setRebuy(true);
            handleOption.setAftersale(true);
        } else {
            throw new IllegalStateException("status Not supported");
        }

        return handleOption;
    }

    public static List<Short> orderStatus(Integer showType) {
        // All orders
        if (showType == 0) {
            return null;
        }

        List<Short> status = new ArrayList<Short>(2);

        if (showType.equals(1)) {
            // Pending order
            status.add((short) 101);
        } else if (showType.equals(2)) {
            // Orders to be shipped
            status.add((short) 201);
        } else if (showType.equals(3)) {
            // Orders to be received
            status.add((short) 301);
        } else if (showType.equals(4)) {
            // Orders to be evaluated
            status.add((short) 401);
//            System timeout automatically cancels，Evaluation should not be supported at this time
//            status.add((short)402);
        } else {
            return null;
        }

        return status;
    }


    public static boolean isCreateStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_CREATE == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean hasPayed(LitemallOrder order) {
        return OrderUtil.STATUS_CREATE != order.getOrderStatus().shortValue()
                && OrderUtil.STATUS_CANCEL != order.getOrderStatus().shortValue()
                && OrderUtil.STATUS_AUTO_CANCEL != order.getOrderStatus().shortValue();
    }

    public static boolean isPayStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_PAY == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isShipStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_SHIP == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isConfirmStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_CONFIRM == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isCancelStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_CANCEL == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isAutoCancelStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_AUTO_CANCEL == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isRefundStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_REFUND == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isRefundConfirmStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_REFUND_CONFIRM == litemallOrder.getOrderStatus().shortValue();
    }

    public static boolean isAutoConfirmStatus(LitemallOrder litemallOrder) {
        return OrderUtil.STATUS_AUTO_CONFIRM == litemallOrder.getOrderStatus().shortValue();
    }
}
