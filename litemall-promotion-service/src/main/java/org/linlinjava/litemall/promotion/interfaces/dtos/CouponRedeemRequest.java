package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for redeeming a held coupon at checkout. The userCouponId and
 * userId are taken from the path / authenticated context, not the body.
 *
 * <p>Wave 18: {@code goodsIds}/{@code categoryIds} are OPTIONAL cart scope
 * facts — when the order service passes them, the coupon's goods scope is
 * re-checked at consumption.
 */
@Getter
@Setter
public class CouponRedeemRequest {

    private Integer orderId;
    private BigDecimal orderSubtotal;
    private List<Integer> goodsIds;
    private List<Integer> categoryIds;
}
