package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin-management view of a coupon definition (crmeb {@code *ManagerResponse}):
 * exposes the full configuration including total, per-user limit and code.
 */
@Getter
@Setter
@Builder
public class CouponManagerDtoResponse {

    private Integer couponId;
    private String name;
    private String description;
    private String tag;
    private Integer total;
    private BigDecimal discount;
    private BigDecimal min;
    private Integer limitPerUser;
    private String type;
    private String status;
    private String goodsType;
    private Integer[] goodsValue;
    private String code;
    private String timeType;
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
