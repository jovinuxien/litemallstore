package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.math.BigDecimal;
import java.util.List;

public interface LitemallCouponVerifyRepository {
    LitemallCoupon verifyCoupon(LitemallCouponId couponId, LitemallUserId userId, LitemallCouponUserId couponUserId, BigDecimal checkedGoodsPrice, List<LitemallCart> cartList);
}
