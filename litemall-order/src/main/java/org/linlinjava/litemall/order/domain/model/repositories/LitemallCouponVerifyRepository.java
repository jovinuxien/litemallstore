package org.linlinjava.litemall.order.domain.model.repositories;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.math.BigDecimal;
import java.util.List;

public interface LitemallCouponVerifyRepository {
    LitemallCoupon verifyCoupon(LitemallCouponId couponId, LitemallUserId userId, LitemallCouponUserId couponUserId, BigDecimal checkedGoodsPrice, List<LitemallCart> cartList);
}
