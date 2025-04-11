package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;


@Getter
@Setter
public class LitemallCouponAggregate {

    private LitemallCouponId couponId;
    private String name;
    private String description;
    private String tag;
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal discount;
    private BigDecimal minPrice;
    private LitemallCouponType type;
    private LitemallCouponStatus status;
    private String code;
    private boolean deleted;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public boolean isExpired(){
        if(type == LitemallCouponType.REGISTER){
            return false; // Register coupon don't expire base on time
        }
        return endTime != null && endTime.isBefore(LocalDateTime.now());
    }

    public boolean isAvailable() {
        return status == LitemallCouponStatus.NORMAL && !deleted && !isExpired();

    }


}
