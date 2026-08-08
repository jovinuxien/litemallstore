package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

/**
 * Body for the group-buy slot mutations (Wave 21):
 * {@code POST /srv/promotion/combination/pink/{pinkId}/attach-order} and
 * {@code POST /srv/promotion/combination/pink/{pinkId}/release} — both take
 * {@code {orderId}} per the Wave-21 contract.
 */
public class PinkOrderRequest {

    private Integer orderId;

    public PinkOrderRequest() {
    }

    public PinkOrderRequest(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }
}
