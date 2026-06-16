package org.linlinjava.litemall.order.interfaces.dtos.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletOperationDtoResponse {

    private boolean success;
    private String message;
    private Integer userId;
    private String operationType;
    private BigDecimal newBalance;

    public static WalletOperationDtoResponse success(Integer userId, String operationType,
                                                      BigDecimal newBalance, String message) {
        return new WalletOperationDtoResponse(true, message, userId, operationType, newBalance);
    }

    public static WalletOperationDtoResponse failure(Integer userId, String operationType, String message) {
        return new WalletOperationDtoResponse(false, message, userId, operationType, null);
    }
}
