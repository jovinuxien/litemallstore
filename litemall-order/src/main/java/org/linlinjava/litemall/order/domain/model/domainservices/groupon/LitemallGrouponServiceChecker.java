package org.linlinjava.litemall.order.domain.model.domainservices.groupon;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponAggregate;
import org.linlinjava.litemall.order.domain.model.domainservices.coupon.LitemallCouponValidationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponStatus;

public class LitemallGrouponServiceChecker {


    private LitemallGrouponValidationResult validateGrouponStatus(LitemallGrouponAggregate groupon) {
        if(groupon.().equals(LitemallCouponStatus.NORMAL)){
            return LitemallCouponValidationResult.invalidStatus();
        }
        return LitemallCouponValidationResult.valid(null);
    }

}
