package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer-facing (H5) view of a coupon definition. Excludes admin-only fields
 * such as total/limit/code.
 */
@Getter
@Setter
@Builder
public class CouponDtoResponse {

    private Integer couponId;
    private String name;
    private String description;
    private String tag;
    private BigDecimal discount;
    private BigDecimal min;
    private String type;
    private String goodsType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
