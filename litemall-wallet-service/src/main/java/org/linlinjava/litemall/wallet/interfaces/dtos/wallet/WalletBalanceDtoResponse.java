package org.linlinjava.litemall.wallet.interfaces.dtos.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceDtoResponse {

    private Integer userId;
    private BigDecimal balance;
    private BigDecimal brokerageBalance;

    public static WalletBalanceDtoResponse of(Integer userId, BigDecimal balance, BigDecimal brokerageBalance) {
        return new WalletBalanceDtoResponse(userId, balance, brokerageBalance);
    }
}
