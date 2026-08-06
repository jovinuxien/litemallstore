package org.linlinjava.litemall.promotion.domain.model.commands.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin command to update an existing coupon definition. Field semantics
 * mirror {@link LitemallIssueCouponCommand}; {@code null} fields are left
 * unchanged.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LitemallUpdateCouponCommand {

    private String name;
    private String description;
    private String tag;
    private Integer total;
    private BigDecimal discount;
    /** Wave 18: 0 = flat amount, 1 = percent rate (1-90); null = unchanged. */
    private Integer discountType;
    /** Wave 18: max absolute discount for percent coupons; null = unchanged. */
    private BigDecimal discountCap;
    private BigDecimal min;
    private Integer limitPerUser;
    private Integer type;
    private Integer status;
    private Integer goodsType;
    private Integer[] goodsValue;
    private String code;
    private Integer timeType;
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
