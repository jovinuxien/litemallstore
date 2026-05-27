package org.linlinjava.litemall.order.application.exceptions;

/**
 * Thrown by the order orchestrator when an order operation fails for a
 * domain/business reason (e.g. invalid command, validation failure, state
 * transition rejected by the aggregate). Distinct from a runtime/system error,
 * which is still surfaced via the generic catch in the orchestrator.
 */
public class LitemallOrderServiceException extends RuntimeException {

    public LitemallOrderServiceException(String message) {
        super(message);
    }

    public LitemallOrderServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
