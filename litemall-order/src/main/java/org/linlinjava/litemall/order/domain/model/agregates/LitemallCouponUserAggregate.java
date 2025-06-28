package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponUserStatus;

import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallCouponUserAggregate {

    private LitemallCouponUserId couponUserId;
    private LitemallUserId userId;

    private LitemallCouponId couponId;
    private LitemallCouponUserStatus status;
    private LocalDateTime usedTime;
    private LitemallOrderId orderId;
    private LocalDateTime addTime;

    public void markAsUsed(){
        this.status = LitemallCouponUserStatus.USED;
        this.usedTime = LocalDateTime.now();
        this.orderId = orderId;
    }

}
