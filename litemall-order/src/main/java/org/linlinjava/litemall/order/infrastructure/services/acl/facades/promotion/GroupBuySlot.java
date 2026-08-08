package org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion;

/**
 * A combination group-buy participation slot ("pink") as promotion reports it
 * ({@code GET /srv/promotion/combination/pink/{pinkId}} —
 * spec-groupon-priced-submit-contract.md). Status is promotion's display string:
 * {@code Pending} | {@code Success} | {@code Failed}.
 */
public class GroupBuySlot {

    private final Integer pinkId;
    private final Integer combinationId;
    private final Integer userId;
    private final Integer orderId;
    private final String status;

    public GroupBuySlot(Integer pinkId, Integer combinationId, Integer userId,
                        Integer orderId, String status) {
        this.pinkId = pinkId;
        this.combinationId = combinationId;
        this.userId = userId;
        this.orderId = orderId;
        this.status = status;
    }

    public Integer getPinkId() {
        return pinkId;
    }

    public Integer getCombinationId() {
        return combinationId;
    }

    public Integer getUserId() {
        return userId;
    }

    /** The order that already consumed this slot, or null while the slot is free. */
    public Integer getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    /** True while the slot may still be paid against (group forming or completed). */
    public boolean isPayable() {
        return "Pending".equalsIgnoreCase(status) || "Success".equalsIgnoreCase(status);
    }
}
