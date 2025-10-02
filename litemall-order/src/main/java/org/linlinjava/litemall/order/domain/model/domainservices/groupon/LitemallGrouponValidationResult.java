package org.linlinjava.litemall.order.domain.model.domainservices.groupon;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;

import java.util.Objects;

public class LitemallGrouponValidationResult {

    public enum GrouponValidationStatusResults {
        VALID,
        INVALID_RULE_NOT_FOUND,
        INVALID_RULE_NOT_ACTIVE,
        INVALID_RULE_EXPIRED,
        INVALID_ALREADY_PARTICIPATED,
        INVALID_USER_NOT_ELIGIBLE,
        INVALID_INSUFFICIENT_INVENTORY,
        INVALID_ORDER_NOT_QUALIFIED,

        GROUPON_FULL,
        GROUPON_JOIN,
        GROUPON_EXPIRED,
        GROUPON_OFFLINE,

    }

    @Getter
    private final GrouponValidationStatusResults status;
    @Getter
    private final String message;
    private final LitemallMoney estimatedDiscount; // Optional: for valid results

    // Private constructor - use static factory methods
    private LitemallGrouponValidationResult(GrouponValidationStatusResults status, String message, LitemallMoney estimatedDiscount) {
        this.status = status;
        this.message = message;
        this.estimatedDiscount = estimatedDiscount;
    }

    // --- Static Factory Methods for Valid Results ---

    public static LitemallGrouponValidationResult valid() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.VALID, "Groupon participation is valid", null);
    }

    public static LitemallGrouponValidationResult validWithDiscount(LitemallMoney estimatedDiscount) {
        Objects.requireNonNull(estimatedDiscount, "Estimated discount must not be null");
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.VALID,
                "Groupon participation is valid with estimated discount", estimatedDiscount);
    }

    // --- Static Factory Methods for Invalid Results ---

    public static LitemallGrouponValidationResult invalidRuleNotFound() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_RULE_NOT_FOUND,
                "Groupon rule not found or deleted", null);
    }

    public static LitemallGrouponValidationResult invalidRuleNotActive() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_RULE_NOT_ACTIVE,
                "Groupon rule is not active", null);
    }

    public static LitemallGrouponValidationResult invalidRuleExpired() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_RULE_EXPIRED,
                "Groupon rule has expired", null);
    }

    public static LitemallGrouponValidationResult grouponJoin() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.GROUPON_JOIN,
                "User has already participated in this groupon activity", null);
    }

    public static LitemallGrouponValidationResult grouponFull() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.GROUPON_FULL,
                "Groupon activity is already full", null);
    }

    public static LitemallGrouponValidationResult grouponOffline() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.GROUPON_OFFLINE,
                "Groupon activity is already full", null);
    }

    public static LitemallGrouponValidationResult grouponDownExpired() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.GROUPON_EXPIRED,
                "Groupon activity is already full", null);
    }

    public static LitemallGrouponValidationResult invalidUserNotEligible() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_USER_NOT_ELIGIBLE,
                "User is not eligible for this groupon", null);
    }

    public static LitemallGrouponValidationResult invalidInsufficientInventory() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_INSUFFICIENT_INVENTORY,
                "Insufficient inventory for groupon purchase", null);
    }

    public static LitemallGrouponValidationResult invalidOrderNotQualified() {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_ORDER_NOT_QUALIFIED,
                "Order does not qualify for groupon discount", null);
    }

    // Generic invalid result for custom messages
    public static LitemallGrouponValidationResult invalid(String message) {
        return new LitemallGrouponValidationResult(GrouponValidationStatusResults.INVALID_USER_NOT_ELIGIBLE, message, null);
    }

    // --- Domain Logic & Accessors ---

    public boolean isValid() {
        return this.status == GrouponValidationStatusResults.VALID;
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
        return status == GrouponValidationStatusResults.INVALID_RULE_NOT_FOUND ||
                status == GrouponValidationStatusResults.INVALID_RULE_NOT_ACTIVE ||
                status == GrouponValidationStatusResults.INVALID_RULE_EXPIRED;
    }

    public boolean isParticipationRelatedFailure() {
        return status == GrouponValidationStatusResults.INVALID_ALREADY_PARTICIPATED ||
                status == GrouponValidationStatusResults.GROUPON_FULL;
    }

    // --- Value Object Contract ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallGrouponValidationResult that = (LitemallGrouponValidationResult) o;
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
