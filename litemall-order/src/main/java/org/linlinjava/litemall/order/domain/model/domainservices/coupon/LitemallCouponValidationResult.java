package org.linlinjava.litemall.order.domain.model.domainservices.coupon;


import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;

import java.util.Objects;

public class LitemallCouponValidationResult {

    public enum Status {
        VALID,
        EXPIRED,
        COUPON_NOT_FOUND,
        COUPON_USER_NOT_FOUND,
        INSUFFICIENT_MIN_AMOUNT,
        INSUFFICIENT_APPLICABLE_GOODS_AMOUNT,
        INVALID_STATUS,
        INVALID_USER_STATUS,
        INVALID
    }

    @Getter
    private final Status status;
    @Getter
    private final String message;

    private final LitemallMoney discountAmount;

    private LitemallCouponValidationResult(Status status, String message, LitemallMoney discountAmount) {
        this.status = status;
        this.message = message;
        this.discountAmount = discountAmount;
    }


    /**
     * *******************My static factory methods*************************
     */

    public static LitemallCouponValidationResult valid(LitemallMoney discountAmount, String message) {
        return new LitemallCouponValidationResult(Status.VALID, message, discountAmount);
    }

    public static LitemallCouponValidationResult expired(String message) {
        return new LitemallCouponValidationResult(Status.EXPIRED, message, null);
    }

    public static LitemallCouponValidationResult couponNotFound() {
        return new LitemallCouponValidationResult(Status.COUPON_NOT_FOUND, "Coupon not found", null);
    }

    public static LitemallCouponValidationResult couponUserNotFound() {
        return new LitemallCouponValidationResult(Status.COUPON_USER_NOT_FOUND, "Coupon user not found", null);
    }

    public static LitemallCouponValidationResult insufficientMinAmount() {
        return new LitemallCouponValidationResult(Status.INSUFFICIENT_MIN_AMOUNT, "Insufficient minimum amount", null);
    }

    public static LitemallCouponValidationResult insufficientApplicableGoodsAmount() {
        return new LitemallCouponValidationResult(Status.INSUFFICIENT_APPLICABLE_GOODS_AMOUNT, "Insufficient applicable goods amount", null);
    }

    public static LitemallCouponValidationResult invalidStatus() {
        return new LitemallCouponValidationResult(Status.INVALID_STATUS, "Invalid coupon status", null);
    }

    public static LitemallCouponValidationResult invalidUserStatus(String message) {
        return new LitemallCouponValidationResult(Status.INVALID_USER_STATUS, message, null);
    }

    public static LitemallCouponValidationResult invalid(String message) {
        return new LitemallCouponValidationResult(Status.INVALID, message, null);
    }


    /**
     * ********************* Domain logic and accessors **********************
     */

    public boolean isValid() {
        return status == Status.VALID;
    }

    /**
     * ********************* Return the discount amount if valid **********************
     */
    public LitemallMoney getDiscountAmount() {
        if(!isValid()){
            throw  new IllegalArgumentException("Cannot get discount amount for an invalid coupon.");
        }
        return discountAmount;
    }

    /**
     * A safer method to get the discount amount, returning a default (like zero) for invalid cases.
     * Useful for UI display where you might want to show $0.00 discount.
     *
     * @param defaultValue the value to return if the result is invalid (e.g., Money.ZERO)
     * @return the discount amount if valid, else the defaultValue
     */
    public LitemallMoney getDiscountAmountOrDefault(LitemallMoney defaultValue) {
        return isValid() ? discountAmount : defaultValue;
    }






    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallCouponValidationResult that = (LitemallCouponValidationResult) o;
        // Compare all fields that define the value's identity.
        return status == that.status &&
                Objects.equals(message, that.message) &&
                Objects.equals(discountAmount, that.discountAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, message, discountAmount);
    }

    @Override
    public String toString() {
        return "CouponValidationResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", discountAmount=" + discountAmount +
                '}';
    }
}
