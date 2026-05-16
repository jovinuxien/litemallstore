package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallCouponUsageLimitExceededException;

public class UsageLimit {

    private  int maxUses;
    private  int currentUses;


    public boolean hasRemainingUses() {
        return currentUses < maxUses;
    }

    public void recordUsage() {
        if (!hasRemainingUses()) {
            throw new LitemallCouponUsageLimitExceededException();
        }
        currentUses++;
    }
}
