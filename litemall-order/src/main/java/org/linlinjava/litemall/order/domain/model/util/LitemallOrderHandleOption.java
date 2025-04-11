package org.linlinjava.litemall.order.domain.model.util;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;


@Getter
@Setter
public class LitemallOrderHandleOption {
    private boolean cancel;
    private boolean pay;
    private boolean delete;
    private boolean refund;
    private boolean confirm;
    private boolean comment;
    private boolean rebuy;
    private boolean aftersale;

    public static LitemallOrderHandleOption forStatus(LitemallOrderStatus status) {
        LitemallOrderHandleOption option = new LitemallOrderHandleOption();

        switch (status) {
            case CREATED:
                option.setCancel(true);
                option.setPay(true);
                break;
            case CANCELLED:
            case SYSTEM_CANCELLED:
                option.setDelete(true);
                break;
            case PAID:
                option.setRefund(true);
                break;
            case SHIPPED:
                option.setConfirm(true);
                break;
            case DELIVERED:
            case AUTO_DELIVERED:
                option.setDelete(true);
                option.setComment(true);
                option.setRebuy(true);
                option.setAftersale(true);
                break;
            case REFUNDED:
                option.setDelete(true);
                break;
            default:
                // No options for other statuses
        }

        return option;
    }
}
