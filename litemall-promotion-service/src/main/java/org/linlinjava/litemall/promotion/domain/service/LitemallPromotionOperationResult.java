package org.linlinjava.litemall.promotion.domain.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LitemallPromotionOperationResult {

    public enum OperationType {
        JOIN_SECKILL, CREATE_BARGAIN, HELP_BARGAIN, CHECK_BARGAIN_STATUS
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
