package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

/**
 * Request body for the Wave-21 order-linkage endpoints on a group slot
 * ("pink"): attach-order carries the order placed against the slot; release
 * carries the order being cancelled (replay-safe guard, mirroring
 * {@link CouponReleaseRequest}).
 */
@Getter
@Setter
public class CombinationOrderRequest {

    private Integer orderId;
}
