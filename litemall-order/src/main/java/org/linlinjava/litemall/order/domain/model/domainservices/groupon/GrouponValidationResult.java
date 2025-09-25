package org.linlinjava.litemall.order.domain.model.domainservices.groupon;

import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;

import java.util.Objects;

public class GrouponValidationResult {

    public enum Status {
        VALID,
        INVALID_RULE_NOT_FOUND,
        INVALID_RULE_NOT_ACTIVE,
        INVALID_RULE_EXPIRED,
        INVALID_ALREADY_PARTICIPATED,
        INVALID_GROUPON_FULL,
        INVALID_USER_NOT_ELIGIBLE,
        INVALID_INSUFFICIENT_INVENTORY,
        INVALID_ORDER_NOT_QUALIFIED
    }

    private final Status status;
    private final String message;
    private final LitemallMoney estimatedDiscount; // Optional: for valid results

    // Private constructor - use static factory methods
    private GrouponValidationResult(Status status, String message, LitemallMoney estimatedDiscount) {
        this.status = status;
        this.message = message;
        this.estimatedDiscount = estimatedDiscount;
    }

    // --- Static Factory Methods for Valid Results ---

    public static GrouponValidationResult valid() {
        return new GrouponValidationResult(Status.VALID, "Groupon participation is valid", null);
    }

    public static GrouponValidationResult validWithDiscount(LitemallMoney estimatedDiscount) {
        Objects.requireNonNull(estimatedDiscount, "Estimated discount must not be null");
        return new GrouponValidationResult(Status.VALID,
                "Groupon participation is valid with estimated discount", estimatedDiscount);
    }

    // --- Static Factory Methods for Invalid Results ---

    public static GrouponValidationResult invalidRuleNotFound() {
        return new GrouponValidationResult(Status.INVALID_RULE_NOT_FOUND,
                "Groupon rule not found or deleted", null);
    }

    public static GrouponValidationResult invalidRuleNotActive() {
        return new GrouponValidationResult(Status.INVALID_RULE_NOT_ACTIVE,
                "Groupon rule is not active", null);
    }

    public static GrouponValidationResult invalidRuleExpired() {
        return new GrouponValidationResult(Status.INVALID_RULE_EXPIRED,
                "Groupon rule has expired", null);
    }

    public static GrouponValidationResult invalidAlreadyParticipated() {
        return new GrouponValidationResult(Status.INVALID_ALREADY_PARTICIPATED,
                "User has already participated in this groupon activity", null);
    }

    public static GrouponValidationResult invalidGrouponFull() {
        return new GrouponValidationResult(Status.INVALID_GROUPON_FULL,
                "Groupon activity is already full", null);
    }

    public static GrouponValidationResult invalidUserNotEligible() {
        return new GrouponValidationResult(Status.INVALID_USER_NOT_ELIGIBLE,
                "User is not eligible for this groupon", null);
    }

    public static GrouponValidationResult invalidInsufficientInventory() {
        return new GrouponValidationResult(Status.INVALID_INSUFFICIENT_INVENTORY,
                "Insufficient inventory for groupon purchase", null);
    }

    public static GrouponValidationResult invalidOrderNotQualified() {
        return new GrouponValidationResult(Status.INVALID_ORDER_NOT_QUALIFIED,
                "Order does not qualify for groupon discount", null);
    }

    // Generic invalid result for custom messages
    public static GrouponValidationResult invalid(String message) {
        return new GrouponValidationResult(Status.INVALID_USER_NOT_ELIGIBLE, message, null);
    }

    // --- Domain Logic & Accessors ---

    public boolean isValid() {
        return this.status == Status.VALID;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Returns the estimated discount if the result is valid.
     * @return the estimated discount
     * @throws IllegalStateException if the result is not valid
     */
    public LitemallMoney getEstimatedDiscount() {
        if (!isValid()) {
            throw new IllegalStateException("Cannot get estimated discount from an invalid validation result. Status: " + status);
        }
        return estimatedDiscount;
    }

    /**
     * Safe method to get discount with default value for invalid results
     */
    public LitemallMoney getEstimatedDiscountOrDefault(LitemallMoney defaultValue) {
        return isValid() && estimatedDiscount != null ? estimatedDiscount : defaultValue;
    }

    // --- Utility Methods for Business Logic ---

    public boolean isRuleRelatedFailure() {
        return status == Status.INVALID_RULE_NOT_FOUND ||
                status == Status.INVALID_RULE_NOT_ACTIVE ||
                status == Status.INVALID_RULE_EXPIRED;
    }

    public boolean isParticipationRelatedFailure() {
        return status == Status.INVALID_ALREADY_PARTICIPATED ||
                status == Status.INVALID_GROUPON_FULL;
    }

    // --- Value Object Contract ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GrouponValidationResult that = (GrouponValidationResult) o;
        return status == that.status &&
                Objects.equals(message, that.message) &&
                Objects.equals(estimatedDiscount, that.estimatedDiscount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, message, estimatedDiscount);
    }

    @Override
    public String toString() {
        return "GrouponValidationResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", estimatedDiscount=" + estimatedDiscount +
                '}';
    }
}
