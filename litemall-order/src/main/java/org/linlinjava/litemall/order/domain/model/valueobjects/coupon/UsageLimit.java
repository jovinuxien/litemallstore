package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;

public class UsageLimit {

    private final int maxUses;
    private final int currentUses;


    public UsageLimit(int maxUses, int currentUses) {
        this.maxUses = maxUses;
        this.currentUses = currentUses;
    }

    public boolean hasRemainingUses() {
        return currentUses < maxUses;
    }
}
