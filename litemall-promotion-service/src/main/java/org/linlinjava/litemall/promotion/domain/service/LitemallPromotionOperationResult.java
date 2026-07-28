package org.linlinjava.litemall.promotion.domain.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LitemallPromotionOperationResult {

    public enum OperationType {
        JOIN_SECKILL, CREATE_BARGAIN, HELP_BARGAIN, CHECK_BARGAIN_STATUS,
        ISSUE_COUPON, RECEIVE_COUPON, REDEEM_COUPON,
        EXCHANGE_COUPON, RELEASE_COUPON, UPDATE_COUPON, DELETE_COUPON, GRANT_COUPON,
        DEFINE_COMBINATION, ACTIVATE_COMBINATION, EXPIRE_COMBINATION,
        UPDATE_COMBINATION, DELETE_COMBINATION,
        START_GROUP, JOIN_GROUP,
        DEFINE_CAMPAIGN, ACTIVATE_CAMPAIGN, EVALUATE_CAMPAIGN,
        COMPLETE_CAMPAIGN, CAMPAIGN_FROM_CATEGORY
    }

    private final boolean success;
    private final OperationType operationType;
    private final String message;
    private final Map<String, Object> data;

    private LitemallPromotionOperationResult(boolean success, OperationType operationType,
                                              String message, Map<String, Object> data) {
        this.success = success;
        this.operationType = operationType;
        this.message = message;
        this.data = data != null ? data : new HashMap<>();
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    public static LitemallPromotionOperationResult success(OperationType operationType, String message) {
        return new LitemallPromotionOperationResult(true, operationType, message, new HashMap<>());
    }

    public static LitemallPromotionOperationResult success(OperationType operationType, String message,
                                                            Map<String, Object> data) {
        return new LitemallPromotionOperationResult(true, operationType, message, data);
    }

    public static LitemallPromotionOperationResult failed(OperationType operationType, String message) {
        return new LitemallPromotionOperationResult(false, operationType, message, new HashMap<>());
    }

    public static LitemallPromotionOperationResult invalidState(OperationType operationType, String reason) {
        return new LitemallPromotionOperationResult(false, operationType,
                "Invalid state: " + reason, new HashMap<>());
    }

    // Convenience factory methods per operation type

    public static LitemallPromotionOperationResult seckillJoinSuccess(Map<String, Object> data) {
        return success(OperationType.JOIN_SECKILL, "Seckill joined successfully", data);
    }

    public static LitemallPromotionOperationResult seckillJoinFailed(String reason) {
        return failed(OperationType.JOIN_SECKILL, "Failed to join seckill: " + reason);
    }

    public static LitemallPromotionOperationResult bargainSessionCreated(Map<String, Object> data) {
        return success(OperationType.CREATE_BARGAIN, "Bargain session created successfully", data);
    }

    public static LitemallPromotionOperationResult bargainSessionFailed(String reason) {
        return failed(OperationType.CREATE_BARGAIN, "Failed to create bargain session: " + reason);
    }

    public static LitemallPromotionOperationResult helpBargainSuccess(Map<String, Object> data) {
        return success(OperationType.HELP_BARGAIN, "Help applied successfully", data);
    }

    public static LitemallPromotionOperationResult helpBargainFailed(String reason) {
        return failed(OperationType.HELP_BARGAIN, "Failed to apply help: " + reason);
    }

    public static LitemallPromotionOperationResult bargainStatusChecked(Map<String, Object> data) {
        return success(OperationType.CHECK_BARGAIN_STATUS, "Bargain status retrieved", data);
    }

    // Coupon vertical

    public static LitemallPromotionOperationResult couponIssued(Map<String, Object> data) {
        return success(OperationType.ISSUE_COUPON, "Coupon issued successfully", data);
    }

    public static LitemallPromotionOperationResult couponIssueFailed(String reason) {
        return failed(OperationType.ISSUE_COUPON, "Failed to issue coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponReceived(Map<String, Object> data) {
        return success(OperationType.RECEIVE_COUPON, "Coupon received successfully", data);
    }

    public static LitemallPromotionOperationResult couponReceiveFailed(String reason) {
        return failed(OperationType.RECEIVE_COUPON, "Failed to receive coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponRedeemed(Map<String, Object> data) {
        return success(OperationType.REDEEM_COUPON, "Coupon redeemed successfully", data);
    }

    public static LitemallPromotionOperationResult couponRedeemFailed(String reason) {
        return failed(OperationType.REDEEM_COUPON, "Failed to redeem coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponExchanged(Map<String, Object> data) {
        return success(OperationType.EXCHANGE_COUPON, "Coupon exchanged successfully", data);
    }

    public static LitemallPromotionOperationResult couponExchangeFailed(String reason) {
        return failed(OperationType.EXCHANGE_COUPON, "Failed to exchange coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponReleased(Map<String, Object> data) {
        return success(OperationType.RELEASE_COUPON, "Coupon released successfully", data);
    }

    public static LitemallPromotionOperationResult couponReleaseFailed(String reason) {
        return failed(OperationType.RELEASE_COUPON, "Failed to release coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponUpdated(Map<String, Object> data) {
        return success(OperationType.UPDATE_COUPON, "Coupon updated successfully", data);
    }

    public static LitemallPromotionOperationResult couponUpdateFailed(String reason) {
        return failed(OperationType.UPDATE_COUPON, "Failed to update coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponDeleted(Map<String, Object> data) {
        return success(OperationType.DELETE_COUPON, "Coupon deleted successfully", data);
    }

    public static LitemallPromotionOperationResult couponDeleteFailed(String reason) {
        return failed(OperationType.DELETE_COUPON, "Failed to delete coupon: " + reason);
    }

    public static LitemallPromotionOperationResult couponGranted(Map<String, Object> data) {
        return success(OperationType.GRANT_COUPON, "Coupon granted successfully", data);
    }

    public static LitemallPromotionOperationResult couponGrantFailed(String reason) {
        return failed(OperationType.GRANT_COUPON, "Failed to grant coupon: " + reason);
    }

    // Combination vertical (campaign definition)

    public static LitemallPromotionOperationResult combinationDefined(Map<String, Object> data) {
        return success(OperationType.DEFINE_COMBINATION, "Combination campaign defined successfully", data);
    }

    public static LitemallPromotionOperationResult combinationDefineFailed(String reason) {
        return failed(OperationType.DEFINE_COMBINATION, "Failed to define combination campaign: " + reason);
    }

    public static LitemallPromotionOperationResult combinationActivated(Map<String, Object> data) {
        return success(OperationType.ACTIVATE_COMBINATION, "Combination campaign activated", data);
    }

    public static LitemallPromotionOperationResult combinationExpired(Map<String, Object> data) {
        return success(OperationType.EXPIRE_COMBINATION, "Combination campaign expired", data);
    }

    public static LitemallPromotionOperationResult combinationStateChangeFailed(String reason) {
        return failed(OperationType.DEFINE_COMBINATION, "Failed to change combination campaign state: " + reason);
    }

    public static LitemallPromotionOperationResult combinationUpdated(Map<String, Object> data) {
        return success(OperationType.UPDATE_COMBINATION, "Combination campaign updated", data);
    }

    public static LitemallPromotionOperationResult combinationDeleted(Map<String, Object> data) {
        return success(OperationType.DELETE_COMBINATION, "Combination campaign deleted", data);
    }

    // Combination participation (group / pink)

    public static LitemallPromotionOperationResult groupStarted(Map<String, Object> data) {
        return success(OperationType.START_GROUP, "Group started successfully", data);
    }

    public static LitemallPromotionOperationResult groupStartFailed(String reason) {
        return failed(OperationType.START_GROUP, "Failed to start group: " + reason);
    }

    public static LitemallPromotionOperationResult groupJoined(Map<String, Object> data) {
        return success(OperationType.JOIN_GROUP, "Group joined successfully", data);
    }

    public static LitemallPromotionOperationResult groupJoinFailed(String reason) {
        return failed(OperationType.JOIN_GROUP, "Failed to join group: " + reason);
    }

    // Campaign vertical (algorithmic targeting)

    public static LitemallPromotionOperationResult campaignDefined(Map<String, Object> data) {
        return success(OperationType.DEFINE_CAMPAIGN, "Campaign defined successfully", data);
    }

    public static LitemallPromotionOperationResult campaignDefineFailed(String reason) {
        return failed(OperationType.DEFINE_CAMPAIGN, "Failed to define campaign: " + reason);
    }

    public static LitemallPromotionOperationResult campaignActivated(Map<String, Object> data) {
        return success(OperationType.ACTIVATE_CAMPAIGN, "Campaign activated", data);
    }

    public static LitemallPromotionOperationResult campaignStateChangeFailed(String reason) {
        return failed(OperationType.ACTIVATE_CAMPAIGN, "Failed to change campaign state: " + reason);
    }

    public static LitemallPromotionOperationResult campaignEvaluated(Map<String, Object> data) {
        return success(OperationType.EVALUATE_CAMPAIGN, "Campaign evaluated; audience assigned", data);
    }

    public static LitemallPromotionOperationResult campaignEvaluateFailed(String reason) {
        return failed(OperationType.EVALUATE_CAMPAIGN, "Failed to evaluate campaign: " + reason);
    }

    public static LitemallPromotionOperationResult campaignCompleted(Map<String, Object> data) {
        return success(OperationType.COMPLETE_CAMPAIGN, "Campaign completed", data);
    }

    public static LitemallPromotionOperationResult campaignCompleteFailed(String reason) {
        return failed(OperationType.COMPLETE_CAMPAIGN, "Failed to complete campaign: " + reason);
    }

    public static LitemallPromotionOperationResult campaignComposedFromCategory(Map<String, Object> data) {
        return success(OperationType.CAMPAIGN_FROM_CATEGORY,
                "Category campaign composed; scheduled for activation", data);
    }

    public static LitemallPromotionOperationResult campaignFromCategoryFailed(String reason) {
        return failed(OperationType.CAMPAIGN_FROM_CATEGORY, "Failed to compose category campaign: " + reason);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public boolean isSuccess() { return success; }
    public OperationType getOperationType() { return operationType; }
    public String getMessage() { return message; }
    public Map<String, Object> getData() { return data; }

    public LitemallPromotionOperationResult withData(String key, Object value) {
        Map<String, Object> newData = new HashMap<>(this.data);
        newData.put(key, value);
        return new LitemallPromotionOperationResult(this.success, this.operationType, this.message, newData);
    }

    // =========================================================================
    // VALUE OBJECT CONTRACT
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallPromotionOperationResult that = (LitemallPromotionOperationResult) o;
        return success == that.success &&
                operationType == that.operationType &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, operationType, message);
    }

    @Override
    public String toString() {
        return "LitemallPromotionOperationResult{" +
                "success=" + success +
                ", operationType=" + operationType +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
