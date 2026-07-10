package org.linlinjava.litemall.order.interfaces.dtos.aftersale;

import java.math.BigDecimal;

/**
 * Body of {@code POST /srv/order/{orderId}/aftersale}. {@code type} follows upstream
 * litemall: 0 = not-received refund, 1 = received, refund only (no return),
 * 2 = return-and-refund. {@code amount} is optional — null means "refund what I
 * paid" — and is capped server-side at the order's actual price. The applicant is
 * ALWAYS the gateway-injected {@code X-User-Id}, never a body field.
 */
public class AftersaleApplyRequestDto {

    private Short type;
    private String reason;
    private BigDecimal amount;
    private String[] pictures;
    private String comment;

    public Short getType() {
        return type;
    }

    public void setType(Short type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String[] getPictures() {
        return pictures;
    }

    public void setPictures(String[] pictures) {
        this.pictures = pictures;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
