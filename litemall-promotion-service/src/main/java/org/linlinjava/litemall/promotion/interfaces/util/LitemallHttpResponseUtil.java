package org.linlinjava.litemall.promotion.interfaces.util;

import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.interfaces.dtos.PromotionOperationDtoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class LitemallHttpResponseUtil {

    private LitemallHttpResponseUtil() {
        // Utility class — no instances
    }

    public static ResponseEntity<PromotionOperationDtoResponse> buildResponse(
            LitemallPromotionOperationResult result) {

        PromotionOperationDtoResponse response = PromotionOperationDtoResponse.builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .operationType(result.getOperationType() != null ?
                        result.getOperationType().name() : null)
                .data(result.getData())
                .build();

        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
