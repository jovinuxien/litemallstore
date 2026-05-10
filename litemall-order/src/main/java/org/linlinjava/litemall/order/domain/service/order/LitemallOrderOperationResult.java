package org.linlinjava.litemall.order.domain.model.domainservices.order;

import org.linlinjava.litemall.order.domain.model.util.LitemallOrderHandleOption;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.util.Objects;

public class LitemallOrderOperationResult {


    public enum OperationType {
        SUBMIT, CANCEL, PAY, REFUND, CONFIRM, SHIP, COMPLETE, UPDATE
    }

    private final boolean success;
    private final OperationType operationType;
    private final LitemallOrderId orderId;
    private final LitemallOrderStatus previousStatus;
    private final LitemallOrderStatus newStatus;
    private final String message;
    private final LitemallOrderHandleOption availableOptions;

    // Private constructor
    private LitemallOrderOperationResult(boolean success, OperationType operationType,
                                         LitemallOrderId orderId, LitemallOrderStatus previousStatus,
                                         LitemallOrderStatus newStatus, String message,
                                         LitemallOrderHandleOption availableOptions) {
        this.success = success;
        this.operationType = operationType;
        this.orderId = orderId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.message = message;
        this.availableOptions = availableOptions;
    }

    // =========================================================================
    // SUCCESS FACTORY METHODS
    // =========================================================================

    // Operation: SUBMIT (Order Creation)
    public static LitemallOrderOperationResult submitSuccess(LitemallOrderId orderId,
                                                             LitemallOrderStatus newStatus,
                                                             LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.SUBMIT, orderId,
                null, newStatus, "Order submitted successfully", availableOptions);
    }

    public static LitemallOrderOperationResult submitSuccessWithPayment(LitemallOrderId orderId,
                                                                        LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.SUBMIT, orderId,
                null, LitemallOrderStatus.CREATED, "Order created. Payment required.", availableOptions);
    }

    public static LitemallOrderOperationResult submitSuccessPaid(LitemallOrderId orderId,
                                                                 LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.SUBMIT, orderId,
                null, LitemallOrderStatus.PAID, "Order created and paid successfully.", availableOptions);
    }

    // =========================================================================
    // CANCEL FACTORY METHODS
    // =========================================================================

    // Operation: CANCEL
    public static LitemallOrderOperationResult cancelSuccess(LitemallOrderId orderId,
                                                             LitemallOrderStatus previousStatus,
                                                             LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.CANCEL, orderId,
                previousStatus, LitemallOrderStatus.CANCELED,
                "Order cancelled successfully", availableOptions);
    }

    public static LitemallOrderOperationResult cancelSuccessBySystem(LitemallOrderId orderId,
                                                                     LitemallOrderStatus previousStatus,
                                                                     String reason) {
        return new LitemallOrderOperationResult(true, OperationType.CANCEL, orderId,
                previousStatus, LitemallOrderStatus.SYSTEM_CANCELED,
                "Order cancelled by system: " + reason,
                LitemallOrderHandleOption.forStatus(LitemallOrderStatus.SYSTEM_CANCELED));
    }

    // =========================================================================
    // PAYMENT FACTORY METHODS
    // =========================================================================

    public static LitemallOrderOperationResult paySuccess(LitemallOrderId orderId,
                                                          LitemallOrderStatus previousStatus,
                                                          LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.PAY, orderId,
                previousStatus, LitemallOrderStatus.PAID,
                "Payment processed successfully", availableOptions);
    }

    public static LitemallOrderOperationResult paySuccessWithGroupon(LitemallOrderId orderId,
                                                                     LitemallOrderStatus previousStatus,
                                                                     LitemallOrderHandleOption availableOptions,
                                                                     String grouponInfo) {
        return new LitemallOrderOperationResult(true, OperationType.PAY, orderId,
                previousStatus, LitemallOrderStatus.PAID,
                "Payment processed successfully. " + grouponInfo, availableOptions);
    }

    public static LitemallOrderOperationResult refundSuccess(LitemallOrderId orderId,
                                                             LitemallOrderStatus previousStatus,
                                                             LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.REFUND, orderId,
                previousStatus, LitemallOrderStatus.REFUNDED,
                "Refund processed successfully", availableOptions);
    }

    public static LitemallOrderOperationResult refundPartialSuccess(LitemallOrderId orderId,
                                                                    LitemallOrderStatus previousStatus,
                                                                    String refundAmount,
                                                                    LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.REFUND, orderId,
                previousStatus, LitemallOrderStatus.REFUNDED,
                "Partial refund processed: " + refundAmount, availableOptions);
    }

    // =========================================================================
    // SHIP FACTORY METHODS
    // =========================================================================

    // Operation: CONFIRM (Delivery Confirmation)
    public static LitemallOrderOperationResult confirmSuccess(LitemallOrderId orderId,
                                                              LitemallOrderStatus previousStatus,
                                                              LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.CONFIRM, orderId,
                previousStatus, LitemallOrderStatus.DELIVERED,
                "Order delivery confirmed", availableOptions);
    }

    public static LitemallOrderOperationResult autoConfirmSuccess(LitemallOrderId orderId,
                                                                  LitemallOrderStatus previousStatus,
                                                                  LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.CONFIRM, orderId,
                previousStatus, LitemallOrderStatus.AUTO_DELIVERED,
                "Order automatically confirmed as delivered", availableOptions);
    }

    // Operation: COMPLETE
    public static LitemallOrderOperationResult completeSuccess(LitemallOrderId orderId,
                                                               LitemallOrderStatus previousStatus,
                                                               LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.COMPLETE, orderId,
                previousStatus, LitemallOrderStatus.DELIVERED,
                "Order completed successfully", availableOptions);
    }

    // Operation: UPDATE (Generic update)
    public static LitemallOrderOperationResult updateSuccess(LitemallOrderId orderId,
                                                             LitemallOrderStatus previousStatus,
                                                             LitemallOrderStatus newStatus,
                                                             String updateDetails,
                                                             LitemallOrderHandleOption availableOptions) {
        return new LitemallOrderOperationResult(true, OperationType.UPDATE, orderId,
                previousStatus, newStatus, "Order updated: " + updateDetails, availableOptions);
    }

    // =========================================================================
    // FAILURE FACTORY METHODS
    // =========================================================================

    // Generic failure methods
    public static LitemallOrderOperationResult operationFailed(OperationType operationType,
                                                               LitemallOrderId orderId,
                                                               String errorMessage) {
        return new LitemallOrderOperationResult(false, operationType, orderId,
                null, null, errorMessage, null);
    }

    // Specific failure scenarios
    public static LitemallOrderOperationResult submitFailed(String errorMessage) {
        return operationFailed(OperationType.SUBMIT, null, errorMessage);
    }

    public static LitemallOrderOperationResult cancelFailed(LitemallOrderId orderId,
                                                            String reason) {
        return operationFailed(OperationType.CANCEL, orderId,
                "Cannot cancel order: " + reason);
    }

    public static LitemallOrderOperationResult payFailed(LitemallOrderId orderId,
                                                         String paymentError) {
        return operationFailed(OperationType.PAY, orderId,
                "Payment failed: " + paymentError);
    }

    public static LitemallOrderOperationResult payFailedInsufficientFunds(LitemallOrderId orderId) {
        return operationFailed(OperationType.PAY, orderId,
                "Payment failed: Insufficient funds");
    }

    public static LitemallOrderOperationResult refundFailed(LitemallOrderId orderId,
                                                            String refundError) {
        return operationFailed(OperationType.REFUND, orderId,
                "Refund failed: " + refundError);
    }

    public static LitemallOrderOperationResult shipFailed(LitemallOrderId orderId,
                                                          String shippingError) {
        return operationFailed(OperationType.SHIP, orderId,
                "Shipping failed: " + shippingError);
    }

    public static LitemallOrderOperationResult confirmFailed(LitemallOrderId orderId,
                                                             String confirmationError) {
        return operationFailed(OperationType.CONFIRM, orderId,
                "Confirmation failed: " + confirmationError);
    }

    public static LitemallOrderOperationResult invalidStateTransition(LitemallOrderId orderId,
                                                                      OperationType operationType,
                                                                      LitemallOrderStatus currentStatus) {
        return operationFailed(operationType, orderId,
                "Cannot perform " + operationType + " on order in " + currentStatus + " status");
    }

    public static LitemallOrderOperationResult orderNotFound(LitemallOrderId orderId) {
        return operationFailed(OperationType.UPDATE, orderId,
                "Order not found");
    }

    // =========================================================================
    // DOMAIN LOGIC & ACCESSORS
    // =========================================================================

    public boolean isSuccess() { return success; }
    public OperationType getOperationType() { return operationType; }
    public LitemallOrderId getOrderId() { return orderId; }
    public LitemallOrderStatus getPreviousStatus() { return previousStatus; }
    public LitemallOrderStatus getNewStatus() { return newStatus; }
    public String getMessage() { return message; }
    public LitemallOrderHandleOption getAvailableOptions() { return availableOptions; }

    public boolean hasStatusChanged() {
        return previousStatus != null && newStatus != null && !previousStatus.equals(newStatus);
    }

    public boolean isStatusTransition() {
        return hasStatusChanged();
    }

    /*public boolean allowsUserAction() {
        return availableOptions != null && availableOptions.hasAnyAction();
    }*/

    // =========================================================================
    // VALUE OBJECT CONTRACT
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallOrderOperationResult that = (LitemallOrderOperationResult) o;
        return success == that.success &&
                operationType == that.operationType &&
                Objects.equals(orderId, that.orderId) &&
                previousStatus == that.previousStatus &&
                newStatus == that.newStatus &&
                Objects.equals(message, that.message) &&
                Objects.equals(availableOptions, that.availableOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, operationType, orderId, previousStatus,
                newStatus, message, availableOptions);
    }

    @Override
    public String toString() {
        return "LitemallOrderOperationResult{" +
                "success=" + success +
                ", operationType=" + operationType +
                ", orderId=" + orderId +
                ", previousStatus=" + previousStatus +
                ", newStatus=" + newStatus +
                ", message='" + message + '\'' +
                ", availableOptions=" + availableOptions +
                '}';
    }
}


