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
    /** The caller's held instance of this coupon; only set on usable-for-checkout views. */
    private Integer userCouponId;
    private String name;
    private String description;
    private String tag;
    /**
     * List/read views: the configured value (flat amount, or percent RATE).
     * Usable-for-checkout views: the COMPUTED effective discount for the
     * passed cart amount (Wave 18) — order-side math is unchanged.
     */
    private BigDecimal discount;
    /** Wave 18: 0 = flat, 1 = percent (rate rides {@code discount}). */
    private Integer discountType;
    /** Wave 18: max absolute discount for percent coupons; null = uncapped. */
    private BigDecimal discountCap;
    /** Wave 18: raw percent rate on usable views (where {@code discount} is the computed amount). */
    private BigDecimal discountRate;
    private BigDecimal min;
    private String type;
    private String goodsType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
