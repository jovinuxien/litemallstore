package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallValidPeriod;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponTimeType;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;


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
    private LitemallCouponTimeType timeType;
    private LitemallCouponStatus status;
    private String code;

    private LitemallValidPeriod validPeriod;

    private Short  goodsType;
    private Integer[] goodsValue;
    private BigDecimal min;

    private boolean deleted;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

}
