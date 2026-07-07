package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin command to define / issue a new coupon. Carries raw values; the
 * application service maps them into a {@code LitemallCouponAggregate}.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LitemallIssueCouponCommand {

    private String name;
    private String description;
    private String tag;
    private Integer total;
    private BigDecimal discount;
    private BigDecimal min;
    private Integer limitPerUser;
    /** litemall-db CouponConstant.TYPE_* code. */
    private Integer type;
    /** litemall-db CouponConstant.GOODS_TYPE_* code. */
    private Integer goodsType;
    private Integer[] goodsValue;
    private String code;
    /** litemall-db CouponConstant.TIME_TYPE_* code. */
    private Integer timeType;
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
