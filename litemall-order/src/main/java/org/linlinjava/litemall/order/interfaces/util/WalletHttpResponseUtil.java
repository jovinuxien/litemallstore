package org.linlinjava.litemall.order.interfaces.util;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.interfaces.dtos.wallet.WalletOperationDtoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
public class WalletHttpResponseUtil {

    public static ResponseEntity<WalletOperationDtoResponse> buildSuccessResponse(
            WalletOperationDtoResponse response, String operationType) {

        log.info("Wallet operation successful: {} for userId={}", operationType, response.getUserId());

        switch (operationType.toUpperCase()) {
            case "CREDIT":
                return ResponseEntity.ok(response);
            case "DEBIT":
                return ResponseEntity.ok(response);
            case "RECHARGE":
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            case "EXTRACT":
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            default:
                return ResponseEntity.ok(response);
        }
    }

    public static ResponseEntity<WalletOperationDtoResponse> buildErrorResponse(
            WalletOperationDtoResponse response, String operationType, Exception e) {

        log.warn("Wallet operation failed: {} - {}", operationType, e.getMessage());

        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else if (e instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
