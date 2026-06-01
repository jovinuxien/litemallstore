package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Customer-facing view of a coupon the user holds.
 */
@Getter
@Setter
@Builder
public class UserCouponDtoResponse {

    private Integer userCouponId;
    private Integer couponId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime usedTime;
    private Integer orderId;
}
